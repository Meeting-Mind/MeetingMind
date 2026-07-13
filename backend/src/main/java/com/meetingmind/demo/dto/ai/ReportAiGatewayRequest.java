package com.meetingmind.demo.dto.ai;

import java.util.List;

public record ReportAiGatewayRequest(
        String projectId,
        String meetingId,
        String title,
        String format,
        List<SourceContext> sources
) {
    public ReportAiGatewayRequest {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public record SourceContext(
            String sourceId,
            String type,
            String meetingId,
            String title,
            String speaker,
            String time,
            Integer startMs,
            Integer endMs,
            String text
    ) {
    }
}
