package com.meetingmind.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record LiveKitTokenRequest(
        @NotBlank String roomName,
        @NotBlank String identity,
        @NotBlank String name
) {
}
