package com.meetingmind.demo.service;

import com.meetingmind.demo.dto.TranscriptEntryResponse;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.config.DotenvConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class SttSessionRegistry {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Path TRANSCRIPT_DIR = Path.of("output", "transcripts");

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();
    // ponytail: 데모 규모 동시 회의 수 가정, 방 하나당 파일 락 하나. 방 수가 많아지면 락 맵을 정리(evict)하는 로직 추가.
    private final Map<String, Object> roomFileLocks = new ConcurrentHashMap<>();
    private final WorkspaceDomainService workspaceDomainService;
    private final SttProvider sttProvider;
    private final LiveKitEgressService liveKitEgressService;
    private final TranscriptAssembler transcriptAssembler;

    public SttSessionRegistry(
            WorkspaceDomainService workspaceDomainService,
            SttProvider sttProvider,
            LiveKitEgressService liveKitEgressService,
            TranscriptAssembler transcriptAssembler
    ) {
        this.workspaceDomainService = workspaceDomainService;
        this.sttProvider = sttProvider;
        this.liveKitEgressService = liveKitEgressService;
        this.transcriptAssembler = transcriptAssembler;
    }

    public String create(String roomName, String displayName) {
        return create(roomName, displayName, null, null);
    }

    public String createMeetingSession(String meetingId, String roomName, String displayName) {
        return create(roomName, displayName, meetingId, null);
    }

    public String createMeetingSession(String meetingId, String roomName, String displayName, String trackId) {
        return create(roomName, displayName, meetingId, trackId);
    }

    private String create(String roomName, String displayName, String meetingId, String trackId) {
        String sessionId = UUID.randomUUID().toString();
        List<TranscriptEntryResponse> transcript = Collections.synchronizedList(new ArrayList<>());
        List<RawTranscriptEvent> rawEvents = Collections.synchronizedList(new ArrayList<>());
        Instant startedAt = Instant.now();
        String speakerLabel = "stt-" + sessionId;

        Consumer<TranscriptEvent> onTranscriptEvent = event -> {
            if (event == null || event.type() == TranscriptEventType.ERROR) {
                return;
            }
            onTranscriptEvent(event, rawEvents, transcript, meetingId, roomName, displayName, speakerLabel, startedAt);
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
                displayName,
                client,
                transcript,
                rawEvents,
                new AtomicReference<>(),
                new AtomicReference<>(trackId),
                new AtomicReference<>(false),
                new AtomicReference<>(false)
        ));
        return sessionId;
    }

    private void persistFinal(
            AssembledTranscriptSegment finalSegment,
            List<TranscriptEntryResponse> transcript,
            String meetingId,
            String roomName,
            String displayName,
            String speakerLabel,
            Instant startedAt
    ) {
        String text = TranscriptTextSanitizer.sanitize(finalSegment.text());
        if (text.isBlank()) {
            return;
        }
        transcript.add(new TranscriptEntryResponse(LocalTime.now().format(TIME_FORMAT), displayName, text));
        if (meetingId != null) {
            int startMs = (int) Math.max(0, finalSegment.startedAtMs());
            int endMs = (int) Math.max(startMs, finalSegment.endedAtMs());
            if (endMs == 0) {
                endMs = Math.max(startMs, (int) java.time.Duration.between(startedAt, Instant.now()).toMillis());
            }
            workspaceDomainService.appendTranscriptSegment(meetingId, speakerLabel, displayName, startMs, endMs, text);
            return;
        }
        appendToTranscriptFile(roomName, displayName, text);
    }

    private void onTranscriptEvent(
            TranscriptEvent event,
            List<RawTranscriptEvent> rawEvents,
            List<TranscriptEntryResponse> transcript,
            String meetingId,
            String roomName,
            String displayName,
            String speakerLabel,
            Instant startedAt
    ) {
        String debugText = TranscriptTextSanitizer.sanitize(event.text());
        if (!debugText.isBlank()) {
            rawEvents.add(new RawTranscriptEvent(
                    event.providerEventId() + "-" + event.sequence(),
                    speakerLabel,
                    displayName,
                    event.isFinal(),
                    (int) Math.max(0, event.startedAtMs()),
                    (int) Math.max(event.startedAtMs(), event.endedAtMs() == null ? event.startedAtMs() : event.endedAtMs()),
                    debugText
            ));
        }
        TranscriptChange change = transcriptAssembler.accept(event);
        for (AssembledTranscriptSegment finalSegment : change.finalized()) {
            persistFinal(finalSegment, transcript, meetingId, roomName, displayName, speakerLabel, startedAt);
        }
    }

    public List<PartialTranscript> getMeetingPartials(String meetingId) {
        List<PartialTranscript> partials = new ArrayList<>();
        for (SessionState state : sessions.values()) {
            if (!meetingId.equals(state.meetingId())) {
                continue;
            }
            transcriptAssembler.partials(state.sessionId()).forEach(partial ->
                    partials.add(new PartialTranscript(state.speakerLabel(), state.displayName(), partial.text()))
            );
        }
        return partials;
    }

    public record PartialTranscript(String speakerLabel, String speakerName, String text) {
    }

    public record RawTranscriptEvent(
            String eventId,
            String speakerLabel,
            String speakerName,
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

    private void appendToTranscriptFile(String roomName, String displayName, String text) {
        Path file = TRANSCRIPT_DIR.resolve(sanitizeFileName(roomName) + ".txt");
        Object lock = roomFileLocks.computeIfAbsent(roomName, key -> new Object());
        synchronized (lock) {
            try {
                Files.createDirectories(TRANSCRIPT_DIR);
                Files.writeString(
                        file,
                        displayName + ": " + text + System.lineSeparator(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }

    private static String sanitizeFileName(String roomName) {
        return roomName.replaceAll("[^a-zA-Z0-9._-]", "_");
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
    }

    public void setTrackId(String sessionId, String trackId) {
        require(sessionId).trackId().set(trackId);
    }

    public void markStopping(String sessionId) {
        require(sessionId).stopping().set(true);
    }

    /**
     * The egress socket may still have queued frames while an explicit stop is
     * closing it. Those frames must not turn a user-requested completion into a
     * provider failure.
     */
    public boolean isStopping(String sessionId) {
        SessionState state = sessions.get(sessionId);
        return state != null && state.stopping().get();
    }

    public String getEgressId(String sessionId) {
        SessionState state = sessions.get(sessionId);
        return state == null ? null : state.egressId().get();
    }

    public List<TranscriptEntryResponse> getSessionTranscript(String sessionId) {
        List<TranscriptEntryResponse> transcript = require(sessionId).transcript();
        synchronized (transcript) {
            return List.copyOf(transcript);
        }
    }

    // ponytail: 세션 수가 적은 데모 규모라 방별 인덱스 없이 전체 스캔. 세션이 많아지면 roomName -> sessionId 인덱스 추가.
    public List<TranscriptEntryResponse> getRoomTranscript(String roomName) {
        List<TranscriptEntryResponse> merged = new ArrayList<>();

        for (SessionState state : sessions.values()) {
            if (!state.roomName().equals(roomName)) {
                continue;
            }
            synchronized (state.transcript()) {
                merged.addAll(state.transcript());
            }
        }

        merged.sort(Comparator.comparing(TranscriptEntryResponse::time));
        return merged;
    }

    public boolean belongsToMeeting(String sessionId, String meetingId) {
        SessionState state = sessions.get(sessionId);
        return state != null && meetingId.equals(state.meetingId());
    }

    public String findActiveMeetingSessionId(String meetingId) {
        return sessions.values().stream()
                .filter(state -> meetingId.equals(state.meetingId()))
                .map(SessionState::sessionId)
                .findFirst()
                .orElse(null);
    }

    public void onEgressClosed(String sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            return;
        }

        if (state.meetingId() == null) {
            state.client().finishAudio();
            return;
        }
        if (!state.stopping().get()) {
            // LiveKit egress can reconnect during an active meeting. Do not send
            // Soniox's end-of-stream marker just because the transport closed.
            if (restartEgress(sessionId, state)) {
                return;
            }
            failAndClose(sessionId);
            return;
        }
        state.client().finishAudio();
        close(sessionId);
    }

    private void handleProviderError(String sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state == null || state.stopping().get()) {
            return;
        }
        failAndClose(sessionId);
    }

    public void close(String sessionId) {
        SessionState state = sessions.remove(sessionId);
        if (state != null) {
            state.client().close();
            for (AssembledTranscriptSegment finalSegment : transcriptAssembler.flush(sessionId)) {
                persistFinal(
                        finalSegment,
                        state.transcript(),
                        state.meetingId(),
                        state.roomName(),
                        state.displayName(),
                        state.speakerLabel(),
                        Instant.now()
                );
            }
            if (state.meetingId() != null && !state.failed().get()) {
                workspaceDomainService.completeMeetingTranscript(state.meetingId());
            }
        }
    }

    public void failAndClose(String sessionId) {
        SessionState state = sessions.remove(sessionId);
        if (state != null) {
            state.client().close();
            transcriptAssembler.discard(sessionId);
            if (state.meetingId() != null && state.failed().compareAndSet(false, true)) {
                workspaceDomainService.failMeetingTranscript(state.meetingId());
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
            String displayName,
            SttStreamClient client,
            List<TranscriptEntryResponse> transcript,
            List<RawTranscriptEvent> rawEvents,
            AtomicReference<String> egressId,
            AtomicReference<String> trackId,
            AtomicReference<Boolean> stopping,
            AtomicReference<Boolean> failed
    ) {
    }

    private boolean restartEgress(String sessionId, SessionState state) {
        String trackId = state.trackId().get();
        if (trackId == null || trackId.isBlank()) {
            return false;
        }
        try {
            String publicWsBaseUrl = DotenvConfig.require("PUBLIC_WS_BASE_URL");
            String websocketUrl = LiveKitEgressService.egressWebSocketUrl(publicWsBaseUrl, sessionId);
            String egressId = liveKitEgressService.startTrackEgress(state.roomName(), trackId, websocketUrl);
            state.egressId().set(egressId);
            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }
}
