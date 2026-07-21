package com.meetingmind.demo.dto;

import java.time.Instant;
import java.util.List;

public record ReportListResponse(List<Report> reports) {
    public record Report(
            String id,
            String meetingId,
            String status,
            String title,
            String summary,
            int version,
            boolean isCurrent,
            Instant createdAt
    ) {
    }
}
