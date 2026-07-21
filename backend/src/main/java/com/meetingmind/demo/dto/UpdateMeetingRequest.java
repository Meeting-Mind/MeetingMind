package com.meetingmind.demo.dto;

import java.time.OffsetDateTime;

public record UpdateMeetingRequest(
        String title,
        String description,
        OffsetDateTime scheduledAt,
        OffsetDateTime scheduledEndAt,
        String status
) {
    public UpdateMeetingRequest(String title, OffsetDateTime scheduledAt, String status) {
        this(title, null, scheduledAt, scheduledAt == null ? null : scheduledAt.plusHours(1), status);
    }
}
