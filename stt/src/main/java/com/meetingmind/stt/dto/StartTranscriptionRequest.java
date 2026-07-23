package com.meetingmind.stt.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record StartTranscriptionRequest(
        @NotBlank String meetingId,
        @NotBlank String roomName,
        @NotBlank String trackId,
        Instant retentionUntil,
        @NotBlank String requestId
) {
}
