package com.meetingmind.demo.dto.ai;

import java.util.List;

public record AiChatResponse(
        String answer,
        List<AiSource> sources,
        boolean unsupported,
        String model
) {
    public AiChatResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
