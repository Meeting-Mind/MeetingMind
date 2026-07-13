package com.meetingmind.demo.dto;

import java.time.Instant;

public record ConfirmMeetingReportResponse(
        String id,
        String status,
        int version,
        boolean isCurrent,
        Instant confirmedAt
) {
}
