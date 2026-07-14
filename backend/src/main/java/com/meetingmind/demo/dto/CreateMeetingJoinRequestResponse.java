package com.meetingmind.demo.dto;

public record CreateMeetingJoinRequestResponse(
        String requestId,
        String meetingId,
        String status
) {
}
