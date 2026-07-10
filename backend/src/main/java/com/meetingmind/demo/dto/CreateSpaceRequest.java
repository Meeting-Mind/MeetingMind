package com.meetingmind.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSpaceRequest(
        @NotBlank String name,
        String description
) {
}
