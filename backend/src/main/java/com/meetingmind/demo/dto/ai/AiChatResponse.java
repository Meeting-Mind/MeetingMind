package com.meetingmind.demo.dto.ai;

import java.util.List;

public record AiChatResponse(
        String answer,
        List<AiSource> sources,
        boolean unsupported,
        String unsupportedReason,
        String model
) {
    public AiChatResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public AiChatResponse(String answer, List<AiSource> sources, boolean unsupported, String model) {
        this(answer, sources, unsupported, null, model);
    }
}
