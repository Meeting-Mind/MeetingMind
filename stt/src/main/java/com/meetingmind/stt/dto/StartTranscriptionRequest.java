package com.meetingmind.stt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record StartTranscriptionRequest(
        @NotBlank String meetingId,
        @NotBlank String roomName,
        @NotBlank String trackId,
        @NotBlank @Size(max = 100) String participantDisplayName,
        Instant retentionUntil,
        @NotBlank String requestId
) {
}
