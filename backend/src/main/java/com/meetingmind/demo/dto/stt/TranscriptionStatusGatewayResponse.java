package com.meetingmind.demo.dto.stt;

import com.meetingmind.demo.domain.TranscriptStatus;

public record TranscriptionStatusGatewayResponse(
        String meetingId,
        TranscriptStatus status
) {
}
