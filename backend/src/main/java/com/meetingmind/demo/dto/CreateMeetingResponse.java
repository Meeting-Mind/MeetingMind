package com.meetingmind.demo.dto;

public record CreateMeetingResponse(
        String id,
        String status,
        String joinCode,
        String joinUrl
) {
}
