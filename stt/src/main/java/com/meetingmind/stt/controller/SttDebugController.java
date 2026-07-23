package com.meetingmind.stt.controller;

import com.meetingmind.stt.config.DotenvConfig;
import com.meetingmind.stt.service.SttSessionRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("stt-debug")
@RequestMapping("/internal/stt/sessions")
public class SttDebugController {

    private final SttSessionRegistry sessionRegistry;

    public SttDebugController(SttSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @GetMapping("/{sessionId}/raw-events")
    public List<SttSessionRegistry.RawTranscriptEvent> rawEvents(
            @RequestHeader(value = "X-STT-Debug-Token", required = false) String token,
            @PathVariable String sessionId
    ) {
        String expectedToken = DotenvConfig.require("STT_DEBUG_TOKEN");
        byte[] expected = expectedToken.getBytes(StandardCharsets.UTF_8);
        byte[] actual = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return sessionRegistry.getSessionRawEvents(sessionId);
    }
}
