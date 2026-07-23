package com.meetingmind.demo.service;

public record SttSessionContext(
        String sessionId,
        String meetingId,
        String participantId,
        String trackId
) {
}
