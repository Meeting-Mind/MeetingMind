package com.meetingmind.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateProjectKnowledgeRequest(
        @NotBlank String type,
        @NotBlank String title,
        @NotBlank String content,
        String sourceMeetingId
) {
}
