package com.meetingmind.demo.dto.ai;

import java.util.List;

public record AiChatResponse(
        String answer,
        List<AiSource> sources,
        boolean unsupported,
        String unsupportedReason,
        String model,
        AiUsageMetrics usage
) {
    public AiChatResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public AiChatResponse(String answer, List<AiSource> sources, boolean unsupported, String model) {
        this(answer, sources, unsupported, null, model, null);
    }

    public record AiUsageMetrics(
            String provider,
            String apiStyle,
            boolean stream,
            int totalMs,
            Integer inputTokens,
            Integer outputTokens,
            Integer outputTokenEstimate
    ) {
    }
}
