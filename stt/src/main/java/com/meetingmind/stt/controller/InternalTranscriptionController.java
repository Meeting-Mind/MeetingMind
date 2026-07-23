package com.meetingmind.stt.controller;

import com.meetingmind.stt.config.DotenvConfig;
import com.meetingmind.stt.domain.MeetingTranscript;
import com.meetingmind.stt.domain.TranscriptSegment;
import com.meetingmind.stt.domain.TranscriptionSession;
import com.meetingmind.stt.dto.ActiveSessionResponse;
import com.meetingmind.stt.dto.PartialResponse;
import com.meetingmind.stt.dto.StartTranscriptionRequest;
import com.meetingmind.stt.dto.TranscriptResponse;
import com.meetingmind.stt.dto.TranscriptionStartResponse;
import com.meetingmind.stt.dto.TranscriptionStatusResponse;
import com.meetingmind.stt.repository.TranscriptionSessionRepository;
import com.meetingmind.stt.service.LiveKitEgressService;
import com.meetingmind.stt.service.SttProvider;
import com.meetingmind.stt.service.SttSessionRegistry;
import com.meetingmind.stt.service.TranscriptionCoordinator;
import com.meetingmind.stt.websocket.EgressTokenService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Core-STT internal contract (see STT-서비스-분리-작업지시서, "Core–STT 내부 계약").
 * Gated to Core's workload identity only by {@link com.meetingmind.stt.auth.SttWorkloadIdentityFilter}.
 * Core is responsible for meeting existence/permission checks before calling here —
 * this controller does not look up MeetingParticipant/ACL itself.
 */
@RestController
@RequestMapping("/internal/v1")
public class InternalTranscriptionController {

    private final SttSessionRegistry sessionRegistry;
    private final TranscriptionCoordinator transcriptionCoordinator;
    private final TranscriptionSessionRepository sessionRepository;
    private final LiveKitEgressService liveKitEgressService;
    private final EgressTokenService egressTokenService;
    private final SttProvider sttProvider;

    public InternalTranscriptionController(
            SttSessionRegistry sessionRegistry,
            TranscriptionCoordinator transcriptionCoordinator,
            TranscriptionSessionRepository sessionRepository,
            LiveKitEgressService liveKitEgressService,
            EgressTokenService egressTokenService,
            SttProvider sttProvider) {
        this.sessionRegistry = sessionRegistry;
        this.transcriptionCoordinator = transcriptionCoordinator;
        this.sessionRepository = sessionRepository;
        this.liveKitEgressService = liveKitEgressService;
        this.egressTokenService = egressTokenService;
        this.sttProvider = sttProvider;
    }

    @PostMapping("/transcriptions")
    public TranscriptionStartResponse start(@Valid @RequestBody StartTranscriptionRequest request) {
        TranscriptionSession existing = sessionRepository.findByRequestId(request.requestId()).orElse(null);
        if (existing != null) {
            return new TranscriptionStartResponse(
                    existing.sessionId(), existing.meetingId(), existing.status().name(), existing.egressId());
        }

        transcriptionCoordinator.startTranscript(request.meetingId(), sttProvider.providerId(), request.retentionUntil());

        String sessionId = sessionRegistry.createMeetingSession(
                request.meetingId(), request.roomName(), request.trackId(), request.requestId());
        try {
            String publicWsBaseUrl = DotenvConfig.require("PUBLIC_WS_BASE_URL");
            String token = egressTokenService.issue(sessionId);
            String websocketUrl = LiveKitEgressService.egressWebSocketUrl(publicWsBaseUrl, sessionId, token);
            String egressId = liveKitEgressService.startTrackEgress(request.roomName(), request.trackId(), websocketUrl);
            sessionRegistry.setEgressId(sessionId, egressId);
            return new TranscriptionStartResponse(sessionId, request.meetingId(), "PROCESSING", egressId);
        } catch (IllegalStateException exception) {
            sessionRegistry.failAndClose(sessionId);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "STT 또는 LiveKit egress 서비스를 시작할 수 없습니다.", exception);
        }
    }

    @PostMapping("/transcriptions/{sessionId}/stop")
    public TranscriptionStatusResponse stop(
            @PathVariable String sessionId,
            @RequestParam(required = false) String meetingId
    ) {
        TranscriptionSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "STT 세션을 찾을 수 없습니다."));
        if (meetingId != null && !meetingId.equals(session.meetingId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "STT 세션을 찾을 수 없습니다.");
        }
        meetingId = session.meetingId();

        switch (session.status()) {
            case COMPLETED, FAILED -> {
                return new TranscriptionStatusResponse(meetingId, transcriptionCoordinator.getTranscript(meetingId).status().name());
            }
            default -> { }
        }

        sessionRegistry.markStopping(sessionId);
        String egressId = sessionRegistry.getEgressId(sessionId);
        try {
            if (egressId != null) {
                liveKitEgressService.stopEgress(egressId);
            }
        } catch (IllegalStateException exception) {
            sessionRegistry.failAndClose(sessionId);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "LiveKit egress 서비스를 중지할 수 없습니다.", exception);
        }
        sessionRegistry.close(sessionId);
        // ponytail: close() only completes the transcript when this Pod owns the live
        // client; retry here (swallowing "already terminal") covers the cross-Pod stop
        // case until sticky egress routing lands.
        try {
            transcriptionCoordinator.completeTranscript(meetingId);
        } catch (ResponseStatusException alreadyTerminal) {
            if (alreadyTerminal.getStatusCode() != HttpStatus.CONFLICT) {
                throw alreadyTerminal;
            }
        }
        return new TranscriptionStatusResponse(meetingId, transcriptionCoordinator.getTranscript(meetingId).status().name());
    }

    @GetMapping("/meetings/{meetingId}/transcript")
    public TranscriptResponse transcript(@PathVariable String meetingId) {
        MeetingTranscript transcript = transcriptionCoordinator.getTranscript(meetingId);
        var segments = transcriptionCoordinator.getSegments(meetingId).stream()
                .map(this::toSegment)
                .toList();
        return new TranscriptResponse(meetingId, transcript.status().name(), segments);
    }

    @GetMapping("/meetings/{meetingId}/transcription-status")
    public TranscriptionStatusResponse status(@PathVariable String meetingId) {
        MeetingTranscript transcript = transcriptionCoordinator.getTranscript(meetingId);
        return new TranscriptionStatusResponse(meetingId, transcript.status().name());
    }

    @GetMapping("/meetings/{meetingId}/active-session")
    public ActiveSessionResponse activeSession(@PathVariable String meetingId) {
        return new ActiveSessionResponse(sessionRegistry.findActiveMeetingSessionId(meetingId));
    }

    @GetMapping("/meetings/{meetingId}/partials")
    public List<PartialResponse> partials(@PathVariable String meetingId) {
        return sessionRegistry.getMeetingPartials(meetingId).stream()
                .map(partial -> new PartialResponse(partial.speakerLabel(), partial.text()))
                .toList();
    }

    private TranscriptResponse.Segment toSegment(TranscriptSegment segment) {
        return new TranscriptResponse.Segment(
                segment.id(), segment.speakerId(), segment.speakerLabel(), segment.speakerName(),
                segment.startMs(), segment.endMs(), segment.text());
    }
}
