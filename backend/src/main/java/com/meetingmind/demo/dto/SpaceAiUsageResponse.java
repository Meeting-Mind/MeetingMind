package com.meetingmind.demo.dto;

import java.util.List;

public record SpaceAiUsageResponse(
        String window,
        Integer limit,
        int totalRequests,
        int totalInputTokens,
        int totalOutputTokens,
        Double usagePercent,
        List<FeatureUsage> features
) {
    public record FeatureUsage(
            String feature,
            int requests,
            int inputTokens,
            int outputTokens
    ) {
    }
}
