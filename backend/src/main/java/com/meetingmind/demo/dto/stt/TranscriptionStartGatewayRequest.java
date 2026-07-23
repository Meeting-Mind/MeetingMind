package com.meetingmind.demo.dto.stt;

public record TranscriptionStartGatewayRequest(
        String meetingId,
        String roomName,
        String trackId,
        String retentionUntil,
        String requestId
) {
}
