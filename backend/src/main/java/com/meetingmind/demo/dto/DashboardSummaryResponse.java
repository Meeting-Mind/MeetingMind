package com.meetingmind.demo.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;

public record DashboardSummaryResponse(
        List<CalendarEvent> todayMeetings,
        List<RecentActivity> recentActivities,
        List<Space> spaces,
        List<Task> actionItems,
        List<LatestReport> latestReports
) {
    public record CalendarEvent(
            String id,
            String spaceId,
            String meetingId,
            String title,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String status
    ) {
    }

    public record RecentActivity(String id, String spaceId, String title, Instant occurredAt, String type) {
    }

    public record Space(String id, String name, String description, String role, long meetingCount, Instant updatedAt) {
    }

    public record Task(
            String id,
            String spaceId,
            String meetingId,
            String title,
            String description,
            String status,
            String assigneeId,
            LocalDate dueDate,
            String sourceCandidateId
    ) {
    }

    public record LatestReport(
            String id,
            String spaceId,
            String meetingId,
            String meetingTitle,
            String title,
            String summary,
            int version,
            Instant confirmedAt
    ) {
    }
}
