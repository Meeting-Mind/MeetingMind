package com.meetingmind.demo.dto;

public record RecordAiUsageEventResponse(
        boolean recorded,
        String spaceId,
        String feature
) {
}
