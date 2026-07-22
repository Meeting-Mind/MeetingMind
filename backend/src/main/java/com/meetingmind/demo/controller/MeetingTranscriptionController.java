package com.meetingmind.demo.controller;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.domain.TranscriptStatus;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.dto.MeetingDialogueResponse;
import com.meetingmind.demo.dto.MeetingTranscriptionStartResponse;
import com.meetingmind.demo.dto.MeetingTranscriptStatusResponse;
import com.meetingmind.demo.dto.StartMeetingTranscriptionRequest;
import com.meetingmind.demo.service.LiveKitEgressService;
import com.meetingmind.demo.service.SttSessionRegistry;
import jakarta.validation.Valid;
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

    private final AuthService authService;
    private final WorkspaceDomainService workspaceDomainService;
    private final SttSessionRegistry sessionRegistry;
    private final LiveKitEgressService liveKitEgressService;

    public MeetingTranscriptionController(
            AuthService authService,
            WorkspaceDomainService workspaceDomainService,
            SttSessionRegistry sessionRegistry,
            LiveKitEgressService liveKitEgressService
    ) {
        this.authService = authService;
        this.workspaceDomainService = workspaceDomainService;
        this.sessionRegistry = sessionRegistry;
        this.liveKitEgressService = liveKitEgressService;
    }

    @PostMapping("/{meetingId}/transcription/start")
    public MeetingTranscriptionStartResponse start(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId,
            @Valid @RequestBody StartMeetingTranscriptionRequest request
    ) {
        AuthUserResponse user = currentUser(authorizationHeader);
        workspaceDomainService.startMeetingTranscript(user.id(), meetingId, "clova-nest");

        String sessionId = null;
        try {
            sessionId = sessionRegistry.createMeetingSession(meetingId, meetingId, user.displayName());
            String publicWsBaseUrl = com.meetingmind.demo.config.DotenvConfig.require("PUBLIC_WS_BASE_URL");
            String egressId = liveKitEgressService.startTrackEgress(
                    meetingId, request.trackId(), publicWsBaseUrl + "/ws/egress-audio/" + sessionId
            );
            sessionRegistry.setEgressId(sessionId, egressId);
            return new MeetingTranscriptionStartResponse(meetingId, TranscriptStatus.PROCESSING.name(), sessionId, egressId);
        } catch (IllegalStateException exception) {
            if (sessionId == null) {
                workspaceDomainService.failMeetingTranscript(meetingId);
            } else {
                sessionRegistry.failAndClose(sessionId);
            }
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
        workspaceDomainService.requireTranscriptManagement(user.id(), meetingId);
        if (!sessionRegistry.belongsToMeeting(sessionId, meetingId)) {
            throw new AuthorizationException(HttpStatus.NOT_FOUND, "STT_SESSION_NOT_FOUND", "STT 세션을 찾을 수 없습니다.");
        }
        String egressId = sessionRegistry.getEgressId(sessionId);
        try {
            if (egressId != null) {
                liveKitEgressService.stopEgress(egressId);
            }
        } catch (IllegalStateException exception) {
            sessionRegistry.failAndClose(sessionId);
            throw new AuthorizationException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "STT_PROVIDER_UNAVAILABLE",
                    "LiveKit egress 서비스를 중지할 수 없습니다."
            );
        }
        sessionRegistry.close(sessionId);
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
                sessionRegistry.getMeetingPartials(meetingId).stream()
                        .map(partial -> new MeetingDialogueResponse.Partial(
                                partial.speakerLabel(), partial.speakerName(), partial.text()
                        ))
                        .toList()
        );
    }

    private AuthUserResponse currentUser(String authorizationHeader) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        workspaceDomainService.ensureUser(user.id(), user.email(), user.displayName(), user.pictureUrl(), user.status());
        return user;
    }
}
