package com.meetingmind.demo.dto;

import java.util.List;

public record TaskCandidateGenerationResponse(
        List<TaskCandidateResponse> candidates,
        List<TaskAssigneeResponse> assignees,
        boolean canConfirm,
        List<Source> sources,
        boolean unsupported,
        String model
) {
    public TaskCandidateGenerationResponse {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        assignees = assignees == null ? List.of() : List.copyOf(assignees);
        sources = sources == null ? List.of() : List.copyOf(sources);
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
