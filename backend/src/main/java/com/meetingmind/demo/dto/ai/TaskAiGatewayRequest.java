package com.meetingmind.demo.dto.ai;

import java.util.List;

public record TaskAiGatewayRequest(
        String projectId,
        String meetingId,
        String title,
        List<Participant> participants,
        List<SourceContext> sources
) {
    public TaskAiGatewayRequest {
        participants = participants == null ? List.of() : List.copyOf(participants);
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public record Participant(String name, String role) {
    }

    public record SourceContext(
            String sourceId,
            String type,
            String projectId,
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
