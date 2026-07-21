package com.meetingmind.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;

public record CreateMeetingRequest(
        @NotBlank String title,
        String description,
        @NotNull OffsetDateTime scheduledAt,
        @NotNull OffsetDateTime scheduledEndAt,
        List<String> participantUserIds
) {
    public CreateMeetingRequest(String title, OffsetDateTime scheduledAt, List<String> participantUserIds) {
        this(title, null, scheduledAt, scheduledAt == null ? null : scheduledAt.plusHours(1), participantUserIds);
    }
}
