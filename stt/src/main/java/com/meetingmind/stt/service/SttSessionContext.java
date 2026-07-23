package com.meetingmind.stt.service;

public record SttSessionContext(
        String sessionId,
        String meetingId,
        String participantId,
        String trackId
) {
}
