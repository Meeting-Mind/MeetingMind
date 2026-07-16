package com.meetingmind.demo.dto.ai;

public record MeetingAiGatewayChatRequest(
        String projectId,
        String meetingId,
        String question
) {
}
