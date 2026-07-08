package com.meetingmind.demo.controller;

import com.meetingmind.demo.config.DotenvConfig;
import com.meetingmind.demo.dto.SttStreamStartRequest;
import com.meetingmind.demo.dto.SttStreamStartResponse;
import com.meetingmind.demo.service.LiveKitEgressService;
import com.meetingmind.demo.service.SttSessionRegistry;
import jakarta.validation.Valid;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/stt/stream")
public class SttStreamController {

    private final SttSessionRegistry sessionRegistry;
    private final LiveKitEgressService liveKitEgressService;

    public SttStreamController(SttSessionRegistry sessionRegistry, LiveKitEgressService liveKitEgressService) {
        this.sessionRegistry = sessionRegistry;
        this.liveKitEgressService = liveKitEgressService;
    }

    @PostMapping("/start")
    public SttStreamStartResponse start(@Valid @RequestBody SttStreamStartRequest request) {
        String sessionId = sessionRegistry.create();
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

    @PostMapping("/{sessionId}/stop")
    public void stop(@PathVariable String sessionId) {
        String egressId = sessionRegistry.getEgressId(sessionId);
        if (egressId != null) {
            liveKitEgressService.stopEgress(egressId);
        }
        sessionRegistry.close(sessionId);
    }

    @GetMapping("/{sessionId}/transcript")
    public String transcript(@PathVariable String sessionId) {
        try {
            return sessionRegistry.getTranscript(sessionId);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }
}
