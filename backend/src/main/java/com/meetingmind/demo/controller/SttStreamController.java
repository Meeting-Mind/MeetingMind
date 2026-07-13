package com.meetingmind.demo.controller;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.config.DotenvConfig;
import com.meetingmind.demo.dto.SttStreamStartRequest;
import com.meetingmind.demo.dto.SttStreamStartResponse;
import com.meetingmind.demo.dto.TranscriptEntryResponse;
import com.meetingmind.demo.service.LiveKitEgressService;
import com.meetingmind.demo.service.SttSessionRegistry;
import jakarta.validation.Valid;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/stt")
public class SttStreamController {

    private final SttSessionRegistry sessionRegistry;
    private final LiveKitEgressService liveKitEgressService;
    private final AuthService authService;

    public SttStreamController(
            SttSessionRegistry sessionRegistry,
            LiveKitEgressService liveKitEgressService,
            AuthService authService) {
        this.sessionRegistry = sessionRegistry;
        this.liveKitEgressService = liveKitEgressService;
        this.authService = authService;
    }

    @PostMapping("/stream/start")
    public SttStreamStartResponse start(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody SttStreamStartRequest request) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        String sessionId = sessionRegistry.create(request.roomName(), user.displayName());
        try {
            String publicWsBaseUrl = DotenvConfig.require("PUBLIC_WS_BASE_URL");
            String websocketUrl = publicWsBaseUrl + "/ws/egress-audio/" + sessionId;
            String egressId = liveKitEgressService.startTrackEgress(request.roomName(), request.trackId(), websocketUrl);
            sessionRegistry.setEgressId(sessionId, egressId);
            return new SttStreamStartResponse(sessionId, egressId);
        } catch (IllegalStateException exception) {
            sessionRegistry.close(sessionId);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), exception);
        }
    }

    // ponytail: LiveKit egress 없이 브라우저 마이크로 바로 붙는 수동 테스트용 엔트리포인트.
    // /ws/egress-audio/{sessionId}에 48kHz PCM을 직접 흘려보내 Clova 스트리밍 경로만 검증한다.
    // 실제 LiveKit 연동 검증이 필요해지면 /stream/start로 대체.
    @PostMapping("/stream/test-start")
    public SttStreamStartResponse testStart(
            @RequestParam(defaultValue = "test-room") String roomName,
            @RequestParam(defaultValue = "tester") String speaker) {
        return new SttStreamStartResponse(sessionRegistry.create(roomName, speaker), null);
    }

    @PostMapping("/stream/{sessionId}/stop")
    public void stop(@PathVariable String sessionId) {
        String egressId = sessionRegistry.getEgressId(sessionId);
        if (egressId != null) {
            liveKitEgressService.stopEgress(egressId);
        }
        sessionRegistry.close(sessionId);
    }

    @GetMapping("/stream/{sessionId}/transcript")
    public List<TranscriptEntryResponse> transcript(@PathVariable String sessionId) {
        try {
            return sessionRegistry.getSessionTranscript(sessionId);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    @GetMapping("/room/{roomName}/transcript")
    public List<TranscriptEntryResponse> roomTranscript(@PathVariable String roomName) {
        return sessionRegistry.getRoomTranscript(roomName);
    }
}
