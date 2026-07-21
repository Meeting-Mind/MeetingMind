package com.meetingmind.demo.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record SpaceDetailResponse(
        String id,
        String name,
        String description,
        String role,
        List<MeetingSummary> upcomingMeetings,
        List<ReportSummary> recentReports,
        List<Task> actionItems,
        List<String> aiEntrypoints
) {
    public record MeetingSummary(
            String id,
            String spaceId,
            String title,
            String description,
            OffsetDateTime scheduledAt,
            OffsetDateTime scheduledEndAt,
            String status,
            String myRole
    ) {
    }

    public record ReportSummary(
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

    public record Task(
            String id,
            String spaceId,
            String meetingId,
            String title,
            String description,
            String status,
            String priority,
            List<String> labels,
            String assigneeId,
            LocalDate dueDate,
            String sourceCandidateId
    ) {
    }
}
