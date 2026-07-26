package com.meetingmind.demo.dto;

public record RecordAiUsageEventRequest(
        String spaceId,
        String meetingId,
        String feature,
        String provider,
        String apiStyle,
        Boolean streamed,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Long totalMs
) {
}
