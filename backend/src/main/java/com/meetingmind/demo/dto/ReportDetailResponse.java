package com.meetingmind.demo.dto;

import java.time.Instant;
import java.util.List;

public record ReportDetailResponse(
        String id,
        String meetingId,
        String status,
        String title,
        String summary,
        String markdown,
        int version,
        boolean isCurrent,
        Instant createdAt,
        Instant confirmedAt,
        List<String> sourceIds
) {
}
