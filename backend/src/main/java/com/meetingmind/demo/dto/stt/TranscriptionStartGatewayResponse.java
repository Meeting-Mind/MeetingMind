package com.meetingmind.demo.dto.stt;

import com.meetingmind.demo.domain.TranscriptStatus;

public record TranscriptionStartGatewayResponse(
        String sessionId,
        TranscriptStatus status
) {
}
