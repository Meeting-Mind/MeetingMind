package com.meetingmind.demo.dto;

public record CreateInstantMeetingResponse(
        String id,
        String status,
        String roomCode,
        String joinCode,
        String joinUrl
) {
}
