package com.meetingmind.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;

public record CreateMeetingRequest(
        @NotBlank String title,
        @NotNull OffsetDateTime scheduledAt,
        List<String> participantUserIds
) {
}
