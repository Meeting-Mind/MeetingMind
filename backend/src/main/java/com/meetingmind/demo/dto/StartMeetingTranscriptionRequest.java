package com.meetingmind.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record StartMeetingTranscriptionRequest(
        @NotBlank @Pattern(regexp = "realtime") String mode,
        @NotBlank String trackId
) {
}
