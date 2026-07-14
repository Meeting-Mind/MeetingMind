package com.meetingmind.demo.dto;

public record ReviewMeetingJoinRequestResponse(
        String requestId,
        String status,
        String participantId,
        String participantType
) {
}
