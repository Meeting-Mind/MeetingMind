package com.meetingmind.demo.controller;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.domain.MeetingTranscript;
import com.meetingmind.demo.domain.TranscriptStatus;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.dto.MeetingDialogueResponse;
import com.meetingmind.demo.dto.MeetingTranscriptionStartResponse;
import com.meetingmind.demo.dto.MeetingTranscriptStatusResponse;
import com.meetingmind.demo.dto.StartMeetingTranscriptionRequest;
import com.meetingmind.demo.gateway.TranscriptionGateway;
import com.meetingmind.demo.gateway.TranscriptionHandle;
import com.meetingmind.demo.gateway.TranscriptionSessionNotFoundException;
import com.meetingmind.demo.gateway.TranscriptionStartCommand;
import com.meetingmind.demo.gateway.TranscriptionStartException;
import com.meetingmind.demo.gateway.TranscriptionStopException;
import com.meetingmind.demo.service.SttProvider;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meetings")
public class MeetingTranscriptionController {

    private static final Logger log = LoggerFactory.getLogger(MeetingTranscriptionController.class);
    private final AuthService authService;
    private final WorkspaceDomainService workspaceDomainService;
    private final TranscriptionGateway transcriptionGateway;
    private final SttProvider sttProvider;

    public MeetingTranscriptionController(
            AuthService authService,
            WorkspaceDomainService workspaceDomainService,
            TranscriptionGateway transcriptionGateway,
            SttProvider sttProvider
    ) {
        this.authService = authService;
        this.workspaceDomainService = workspaceDomainService;
        this.transcriptionGateway = transcriptionGateway;
        this.sttProvider = sttProvider;
    }

    @PostMapping("/{meetingId}/transcription/start")
    public MeetingTranscriptionStartResponse start(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId,
            @Valid @RequestBody StartMeetingTranscriptionRequest request
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        MeetingTranscript transcript = workspaceDomainService.startMeetingTranscript(user.id(), meetingId, sttProvider.providerId());

        try {
            String roomName = workspaceDomainService.meetingRoomName(meetingId);
            TranscriptionHandle handle = transcriptionGateway.start(new TranscriptionStartCommand(
                    meetingId,
                    roomName,
                    request.trackId(),
                    user.displayName(),
                    transcript.retentionUntil(),
                    UUID.randomUUID().toString()
            ));
            return new MeetingTranscriptionStartResponse(meetingId, TranscriptStatus.PROCESSING.name(), handle.sessionId(), handle.egressId());
        } catch (TranscriptionStartException exception) {
            if (exception.sessionId() == null) {
                workspaceDomainService.failMeetingTranscript(meetingId);
            }
            log.warn(
                    "Meeting transcription start rejected by STT gateway. meetingId={} trackId={} reason={}",
                    meetingId,
                    request.trackId(),
                    exception.getMessage()
            );
            throw new AuthorizationException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "STT_PROVIDER_UNAVAILABLE",
                    "STT 또는 LiveKit egress 서비스를 시작할 수 없습니다."
            );
        }
    }

    @PostMapping("/{meetingId}/transcription/{sessionId}/stop")
    public MeetingTranscriptStatusResponse stop(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId,
            @PathVariable String sessionId
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        return stopActiveSession(user, meetingId, sessionId);
    }

    @PostMapping("/{meetingId}/transcription/stop")
    public MeetingTranscriptStatusResponse stopActive(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        String sessionId = transcriptionGateway.activeSessionId(meetingId);
        if (sessionId == null) {
            throw new AuthorizationException(HttpStatus.NOT_FOUND, "STT_SESSION_NOT_FOUND", "STT 세션을 찾을 수 없습니다.");
        }
        return stopActiveSession(user, meetingId, sessionId);
    }

    private MeetingTranscriptStatusResponse stopActiveSession(AuthUserResponse user, String meetingId, String sessionId) {
        workspaceDomainService.requireTranscriptManagement(user.id(), meetingId);
        try {
            transcriptionGateway.stop(meetingId, sessionId);
        } catch (TranscriptionSessionNotFoundException exception) {
            throw new AuthorizationException(HttpStatus.NOT_FOUND, "STT_SESSION_NOT_FOUND", "STT 세션을 찾을 수 없습니다.");
        } catch (TranscriptionStopException exception) {
            log.warn(
                    "Meeting transcription stop rejected by STT gateway. meetingId={} sessionId={} reason={}",
                    meetingId,
                    sessionId,
                    exception.getMessage()
            );
            throw new AuthorizationException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "STT_PROVIDER_UNAVAILABLE",
                    "LiveKit egress 서비스를 중지할 수 없습니다."
            );
        }
        return new MeetingTranscriptStatusResponse(
                meetingId,
                workspaceDomainService.meetingTranscript(user.id(), meetingId).transcript().status().name()
        );
    }

    @GetMapping("/{meetingId}/dialogue")
    public MeetingDialogueResponse dialogue(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        WorkspaceDomainService.MeetingTranscriptView view = workspaceDomainService.meetingTranscript(user.id(), meetingId);
        return new MeetingDialogueResponse(
                meetingId,
                view.transcript().status().name(),
                view.segments().stream()
                        .map(segment -> new MeetingDialogueResponse.Row(
                                segment.id(), segment.speakerId(), segment.speakerLabel(), segment.speakerName(),
                                segment.startMs(), segment.endMs(), segment.text()
                        ))
                        .toList(),
                transcriptionGateway.partials(meetingId)
        );
    }

    private AuthUserResponse currentUser(String authorizationHeader) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        workspaceDomainService.ensureUser(user.id(), user.email(), user.displayName(), user.pictureUrl(), user.status());
        return user;
    }
}
