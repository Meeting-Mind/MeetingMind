package com.meetingmind.demo.dto;

public record UpdateMeetingResponse(
        String id,
        String title,
        String description,
        String scheduledAt,
        String scheduledEndAt,
        String status
) {
}
