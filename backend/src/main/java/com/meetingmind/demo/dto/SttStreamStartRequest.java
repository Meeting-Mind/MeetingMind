package com.meetingmind.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record SttStreamStartRequest(
        @NotBlank String roomName,
        @NotBlank String trackId,
        @NotBlank String speaker
) {
}
