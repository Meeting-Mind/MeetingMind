package com.meetingmind.demo.dto.ai;

import java.util.List;

public record TaskAiGatewayResponse(
        List<Task> tasks,
        List<Source> sources,
        boolean unsupported,
        String model
) {
    public TaskAiGatewayResponse {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public record Task(
            String title,
            String assignee,
            String dueDate,
            List<String> sourceIds,
            String confirmationState
    ) {
        public Task {
            sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
        }
    }

    public record Source(
            String sourceId,
            String type,
            String title,
            String speaker,
            String time,
            Integer startMs,
            Integer endMs,
            String text
    ) {
    }
}
