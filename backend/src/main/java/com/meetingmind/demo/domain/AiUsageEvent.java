package com.meetingmind.demo.domain;

import java.time.Instant;

public record AiUsageEvent(
        String id,
        String spaceId,
        String meetingId,
        AiUsageFeature feature,
        String provider,
        String apiStyle,
        boolean streamed,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Long totalMs,
        Instant createdAt
) {
}
