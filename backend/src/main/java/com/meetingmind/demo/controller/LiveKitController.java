package com.meetingmind.demo.controller;

import com.meetingmind.demo.dto.LiveKitTokenRequest;
import com.meetingmind.demo.dto.LiveKitTokenResponse;
import com.meetingmind.demo.service.LiveKitTokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/livekit")
public class LiveKitController {

    private final LiveKitTokenService liveKitTokenService;

    public LiveKitController(LiveKitTokenService liveKitTokenService) {
        this.liveKitTokenService = liveKitTokenService;
    }

    @PostMapping("/token")
    public LiveKitTokenResponse issueToken(@Valid @RequestBody LiveKitTokenRequest request) {
        try {
            return liveKitTokenService.issueToken(request);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), exception);
        }
    }
}
