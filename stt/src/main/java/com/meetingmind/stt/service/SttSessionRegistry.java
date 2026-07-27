package com.meetingmind.stt.service;

import com.meetingmind.stt.config.DotenvConfig;
import com.meetingmind.stt.domain.TranscriptionSession;
import com.meetingmind.stt.domain.TranscriptionSessionStatus;
import com.meetingmind.stt.repository.TranscriptionSessionRepository;
import com.meetingmind.stt.websocket.EgressTokenService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Holds the live per-Pod STT session state (the CLOVA gRPC stream client can
 * only live in this Pod's memory). Everything needed to find/stop a session
 * from another Pod, or to notice a Pod died mid-session, is mirrored into the
 * transcription_sessions table via {@link TranscriptionSessionRepository}.
 *
 * ponytail: cross-Pod routing of the LiveKit egress WebSocket itself (so the
 * Pod that owns the live client is the one that receives the audio) needs
 * sticky/session-affinity routing at the ingress — out of scope for this
 * service; add when running >1 replica without an LB that supports it.
 */
@Component
public class SttSessionRegistry {

    private static final int HEARTBEAT_STALE_SECONDS = 90;
    private static final List<String> ACTIVE_SESSION_STATUSES = List.of(
            TranscriptionSessionStatus.ACTIVE.name(),
            TranscriptionSessionStatus.STOPPING.name()
    );

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();
    private final TranscriptionCoordinator transcriptionCoordinator;
    private final TranscriptionSessionRepository sessionRepository;
    private final SttProvider sttProvider;
    private final LiveKitEgressService liveKitEgressService;
    private final EgressTokenService egressTokenService;
    private final TranscriptAssembler transcriptAssembler;

    public SttSessionRegistry(
            TranscriptionCoordinator transcriptionCoordinator,
            TranscriptionSessionRepository sessionRepository,
            SttProvider sttProvider,
            LiveKitEgressService liveKitEgressService,
            EgressTokenService egressTokenService,
            TranscriptAssembler transcriptAssembler
    ) {
        this.transcriptionCoordinator = transcriptionCoordinator;
        this.sessionRepository = sessionRepository;
        this.sttProvider = sttProvider;
        this.liveKitEgressService = liveKitEgressService;
        this.egressTokenService = egressTokenService;
        this.transcriptAssembler = transcriptAssembler;
    }

    public String createMeetingSession(
            String meetingId,
            String roomName,
            String trackId,
            String requestId,
            String participantDisplayName
    ) {
        String sessionId = UUID.randomUUID().toString();
        List<RawTranscriptEvent> rawEvents = Collections.synchronizedList(new ArrayList<>());
        Instant startedAt = Instant.now();
        String speakerLabel = participantDisplayName.trim();
        int segmentOffsetMs = transcriptionCoordinator.nextSegmentOffsetMs(meetingId);

        Consumer<TranscriptEvent> onTranscriptEvent = event -> {
            if (event == null || event.type() == TranscriptEventType.ERROR) {
                return;
            }
            onTranscriptEvent(event, rawEvents, meetingId, speakerLabel, startedAt, segmentOffsetMs);
        };
        Consumer<Throwable> onError = ignored -> handleProviderError(sessionId);

        SttStreamClient client = sttProvider.createClient(
                new SttSessionContext(sessionId, meetingId, null, trackId),
                onTranscriptEvent,
                onError
        );

        sessions.put(sessionId, new SessionState(
                sessionId,
                meetingId,
                roomName,
                speakerLabel,
                client,
                rawEvents,
                new AtomicReference<>(),
                new AtomicReference<>(trackId),
                new AtomicReference<>(false),
                new AtomicReference<>(false),
                segmentOffsetMs
        ));
        sessionRepository.save(new TranscriptionSession(
                sessionId, meetingId, roomName, trackId, null,
                TranscriptionSessionStatus.ACTIVE, requestId, startedAt, startedAt));
        return sessionId;
    }

    private void persistFinal(
            AssembledTranscriptSegment finalSegment,
            String meetingId,
            String speakerLabel,
            Instant startedAt,
            int segmentOffsetMs
    ) {
        String text = TranscriptTextSanitizer.sanitize(finalSegment.text());
        if (text.isBlank()) {
            return;
        }
        int startMs = segmentOffsetMs + (int) Math.max(0, finalSegment.startedAtMs());
        int endMs = segmentOffsetMs + (int) Math.max(finalSegment.startedAtMs(), finalSegment.endedAtMs());
        if (endMs == segmentOffsetMs) {
            endMs = Math.max(startMs, segmentOffsetMs
                    + (int) java.time.Duration.between(startedAt, Instant.now()).toMillis());
        }
        transcriptionCoordinator.appendSegment(meetingId, speakerLabel, speakerLabel, startMs, endMs, text);
    }

    private void onTranscriptEvent(
            TranscriptEvent event,
            List<RawTranscriptEvent> rawEvents,
            String meetingId,
            String speakerLabel,
            Instant startedAt,
            int segmentOffsetMs
    ) {
        String debugText = TranscriptTextSanitizer.sanitize(event.text());
        if (!debugText.isBlank()) {
            rawEvents.add(new RawTranscriptEvent(
                    event.providerEventId() + "-" + event.sequence(),
                    speakerLabel,
                    event.isFinal(),
                    segmentOffsetMs + (int) Math.max(0, event.startedAtMs()),
                    segmentOffsetMs + (int) Math.max(
                            event.startedAtMs(), event.endedAtMs() == null ? event.startedAtMs() : event.endedAtMs()),
                    debugText
            ));
        }
        TranscriptChange change = transcriptAssembler.accept(event);
        for (AssembledTranscriptSegment finalSegment : change.finalized()) {
            persistFinal(finalSegment, meetingId, speakerLabel, startedAt, segmentOffsetMs);
        }
    }

    public List<PartialTranscript> getMeetingPartials(String meetingId) {
        List<PartialTranscript> partials = new ArrayList<>();
        for (SessionState state : sessions.values()) {
            if (!meetingId.equals(state.meetingId())) {
                continue;
            }
            transcriptAssembler.partials(state.sessionId()).forEach(partial ->
                    partials.add(new PartialTranscript(state.speakerLabel(), partial.text()))
            );
        }
        return partials;
    }

    public record PartialTranscript(String speakerLabel, String text) {
    }

    public record RawTranscriptEvent(
            String eventId,
            String speakerLabel,
            boolean finalEvent,
            int startMs,
            int endMs,
            String text
    ) {
    }

    public List<RawTranscriptEvent> getSessionRawEvents(String sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            return List.of();
        }
        synchronized (state.rawEvents()) {
            return List.copyOf(state.rawEvents());
        }
    }

    public SttStreamClient getStreamClient(String sessionId) {
        SessionState state = sessions.get(sessionId);
        return state == null ? null : state.client();
    }

    public SttSessionContext getSessionContext(String sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            return null;
        }
        return new SttSessionContext(sessionId, state.meetingId(), null, state.trackId().get());
    }

    public void setEgressId(String sessionId, String egressId) {
        require(sessionId).egressId().set(egressId);
        touchSessionRow(sessionId, row -> row.setEgressId(egressId));
    }

    public void markStopping(String sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state != null) {
            state.stopping().set(true);
        }
        touchSessionRow(sessionId, row -> row.setStatus(TranscriptionSessionStatus.STOPPING));
    }

    /**
     * The egress socket may still have queued frames while an explicit stop is
     * closing it. Those frames must not turn a user-requested completion into a
     * provider failure.
     */
    public boolean isStopping(String sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state != null) {
            return state.stopping().get();
        }
        return sessionRepository.findById(sessionId)
                .map(row -> row.status() == TranscriptionSessionStatus.STOPPING)
                .orElse(false);
    }

    /** Falls back to the shared DB row so a stop request landing on a different Pod still finds the egress. */
    public String getEgressId(String sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state != null) {
            return state.egressId().get();
        }
        return sessionRepository.findById(sessionId).map(TranscriptionSession::egressId).orElse(null);
    }

    public boolean belongsToMeeting(String sessionId, String meetingId) {
        SessionState state = sessions.get(sessionId);
        if (state != null) {
            return meetingId.equals(state.meetingId());
        }
        return sessionRepository.findById(sessionId).map(row -> meetingId.equals(row.meetingId())).orElse(false);
    }

    public String findActiveMeetingSessionId(String meetingId) {
        return sessions.values().stream()
                .filter(state -> meetingId.equals(state.meetingId()))
                .map(SessionState::sessionId)
                .findFirst()
                .orElseGet(() -> sessionRepository
                        .findByMeetingIdAndStatusIn(meetingId, ACTIVE_SESSION_STATUSES)
                        .stream()
                        .findFirst()
                        .map(TranscriptionSession::sessionId)
                        .orElse(null));
    }

    public void onEgressClosed(String sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            return;
        }

        state.client().finishAudio();
        if (!state.stopping().get()) {
            if (restartEgress(sessionId, state)) {
                return;
            }
            failAndClose(sessionId);
            return;
        }
        close(sessionId);
    }

    private void handleProviderError(String sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state == null || state.stopping().get()) {
            return;
        }
        failAndClose(sessionId);
    }

    /** Idempotent: closing an already-closed/unknown session (e.g. duplicate stop) is a no-op. */
    public void close(String sessionId) {
        SessionState state = sessions.remove(sessionId);
        TranscriptionSession row = sessionRepository.findById(sessionId).orElse(null);
        if (state == null && (row == null || isTerminal(row.status()))) {
            return;
        }
        String meetingId = state == null ? row.meetingId() : state.meetingId();
        if (state != null) {
            state.client().close();
            for (AssembledTranscriptSegment finalSegment : transcriptAssembler.flush(sessionId)) {
                persistFinal(
                        finalSegment,
                        state.meetingId(),
                        state.speakerLabel(),
                        Instant.now(),
                        state.segmentOffsetMs()
                );
            }
        }
        touchSessionRow(sessionId, session -> session.setStatus(TranscriptionSessionStatus.COMPLETED));
        if ((state == null || !state.failed().get()) && !hasOtherActiveSession(meetingId, sessionId)) {
            completeTranscriptIfProcessing(meetingId);
        }
    }

    /** Idempotent: failing an already-closed/unknown session is a no-op. */
    public void failAndClose(String sessionId) {
        SessionState state = sessions.remove(sessionId);
        TranscriptionSession row = sessionRepository.findById(sessionId).orElse(null);
        if (state == null && (row == null || isTerminal(row.status()))) {
            return;
        }
        String meetingId = state == null ? row.meetingId() : state.meetingId();
        boolean newlyFailed = state == null || state.failed().compareAndSet(false, true);
        if (state != null) {
            state.client().close();
            transcriptAssembler.discard(sessionId);
        }
        touchSessionRow(sessionId, session -> session.setStatus(TranscriptionSessionStatus.FAILED));
        if (newlyFailed && !hasOtherActiveSession(meetingId, sessionId)) {
            failTranscriptIfProcessing(meetingId);
        }
    }

    private boolean hasOtherActiveSession(String meetingId, String excludingSessionId) {
        boolean inThisPod = sessions.values().stream()
                .anyMatch(state -> meetingId.equals(state.meetingId())
                        && !excludingSessionId.equals(state.sessionId()));
        if (inThisPod) {
            return true;
        }
        return sessionRepository.findByMeetingIdAndStatusIn(meetingId, ACTIVE_SESSION_STATUSES).stream()
                .anyMatch(row -> !excludingSessionId.equals(row.sessionId()));
    }

    private static boolean isTerminal(TranscriptionSessionStatus status) {
        return status == TranscriptionSessionStatus.COMPLETED || status == TranscriptionSessionStatus.FAILED;
    }

    private void completeTranscriptIfProcessing(String meetingId) {
        try {
            transcriptionCoordinator.completeTranscript(meetingId);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() != HttpStatus.CONFLICT) {
                throw exception;
            }
        }
    }

    private void failTranscriptIfProcessing(String meetingId) {
        try {
            transcriptionCoordinator.failTranscript(meetingId);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() != HttpStatus.CONFLICT) {
                throw exception;
            }
        }
    }

    private void touchSessionRow(String sessionId, Consumer<TranscriptionSession> mutator) {
        sessionRepository.findById(sessionId).ifPresent(row -> {
            mutator.accept(row);
            row.setUpdatedAt(Instant.now());
            sessionRepository.save(row);
        });
    }

    /** Coarse liveness signal for {@link #reapStaleSessions()} — touches only this Pod's in-memory sessions. */
    @Scheduled(fixedDelay = 15_000)
    void heartbeat() {
        for (String sessionId : sessions.keySet()) {
            touchSessionRow(sessionId, row -> { });
        }
    }

    /**
     * A crashed Pod stops heartbeating its sessions; this sweep (safe to run on every
     * Pod — a stale row is only ever reaped once) marks them FAILED instead of leaving
     * them PROCESSING forever.
     */
    @Scheduled(fixedDelay = 30_000)
    void reapStaleSessions() {
        Instant staleBefore = Instant.now().minusSeconds(HEARTBEAT_STALE_SECONDS);
        for (TranscriptionSession row : sessionRepository.findAll()) {
            boolean active = row.status() == TranscriptionSessionStatus.ACTIVE
                    || row.status() == TranscriptionSessionStatus.STOPPING;
            if (active && row.updatedAt().isBefore(staleBefore) && !sessions.containsKey(row.sessionId())) {
                row.setStatus(TranscriptionSessionStatus.FAILED);
                row.setUpdatedAt(Instant.now());
                sessionRepository.save(row);
                if (!hasOtherActiveSession(row.meetingId(), row.sessionId())) {
                    try {
                        transcriptionCoordinator.failTranscript(row.meetingId());
                    } catch (RuntimeException ignored) {
                        // transcript already terminal (completed/failed) — nothing to reconcile.
                    }
                }
            }
        }
    }

    private SessionState require(String sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            throw new NoSuchElementException("세션을 찾을 수 없습니다: " + sessionId);
        }
        return state;
    }

    private record SessionState(
            String sessionId,
            String meetingId,
            String roomName,
            String speakerLabel,
            SttStreamClient client,
            List<RawTranscriptEvent> rawEvents,
            AtomicReference<String> egressId,
            AtomicReference<String> trackId,
            AtomicReference<Boolean> stopping,
            AtomicReference<Boolean> failed,
            int segmentOffsetMs
    ) {
    }

    private boolean restartEgress(String sessionId, SessionState state) {
        String trackId = state.trackId().get();
        if (trackId == null || trackId.isBlank()) {
            return false;
        }
        try {
            String publicWsBaseUrl = DotenvConfig.require("PUBLIC_WS_BASE_URL");
            String token = egressTokenService.issue(sessionId);
            String websocketUrl = LiveKitEgressService.egressWebSocketUrl(publicWsBaseUrl, sessionId, token);
            String egressId = liveKitEgressService.startTrackEgress(state.roomName(), trackId, websocketUrl);
            state.egressId().set(egressId);
            touchSessionRow(sessionId, row -> row.setEgressId(egressId));
            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }
}
