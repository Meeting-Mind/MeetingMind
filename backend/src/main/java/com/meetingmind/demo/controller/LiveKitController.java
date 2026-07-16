package com.meetingmind.demo.controller;

import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.dto.LiveKitTokenRequest;
import com.meetingmind.demo.dto.LiveKitTokenResponse;
import com.meetingmind.demo.service.LiveKitTokenService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("legacy-livekit")
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
            throw new AuthorizationException(HttpStatus.SERVICE_UNAVAILABLE, "LIVEKIT_NOT_CONFIGURED", exception.getMessage());
        }
    }
}
