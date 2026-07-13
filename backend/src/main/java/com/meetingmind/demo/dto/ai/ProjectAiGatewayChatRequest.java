package com.meetingmind.demo.dto.ai;

import java.util.List;

public record ProjectAiGatewayChatRequest(
        String projectId,
        String question,
        List<String> allowedMeetingIds,
        List<SourceContext> sources
) {
    public ProjectAiGatewayChatRequest {
        allowedMeetingIds = allowedMeetingIds == null ? List.of() : List.copyOf(allowedMeetingIds);
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public record SourceContext(
            String sourceId,
            String type,
            String projectId,
            String meetingId,
            String title,
            String text
    ) {
    }
}
