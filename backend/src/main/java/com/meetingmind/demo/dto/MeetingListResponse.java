package com.meetingmind.demo.dto;

import java.util.List;

public record MeetingListResponse(List<MeetingSummary> meetings) {

    public record MeetingSummary(
            String id,
            String spaceId,
            String title,
            String description,
            String scheduledAt,
            String scheduledEndAt,
            String status,
            String myRole
    ) {
    }
}
