package com.meetingmind.demo.dto;

public record UpdateMeetingResponse(
        String id,
        String title,
        String scheduledAt,
        String status
) {
}
