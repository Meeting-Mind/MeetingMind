package com.meetingmind.demo.dto.ai;

public record MeetingAiGatewayTermRequest(
        String projectId,
        String meetingId,
        String term
) {
}
