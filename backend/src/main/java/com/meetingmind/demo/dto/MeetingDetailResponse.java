package com.meetingmind.demo.dto;

public record MeetingDetailResponse(
        String id,
        String title,
        String roomCode,
        String scheduledAt,
        String status
) {
}
