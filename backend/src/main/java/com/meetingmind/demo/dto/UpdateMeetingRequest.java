package com.meetingmind.demo.dto;

import java.time.OffsetDateTime;

public record UpdateMeetingRequest(
        String title,
        OffsetDateTime scheduledAt,
        String status
) {
}
