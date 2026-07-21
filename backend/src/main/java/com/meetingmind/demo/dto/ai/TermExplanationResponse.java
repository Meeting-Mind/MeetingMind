package com.meetingmind.demo.dto.ai;

import java.util.List;

public record TermExplanationResponse(
        String term,
        String explanation,
        String sourceType,
        List<AiSource> sources,
        boolean unsupported,
        String unsupportedReason,
        String model
) {
    public TermExplanationResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
