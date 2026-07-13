package com.meetingmind.demo.dto;

import java.util.List;

public record TaskCandidatesResponse(
        List<TaskCandidateResponse> candidates,
        List<TaskAssigneeResponse> assignees,
        boolean canConfirm
) {
    public TaskCandidatesResponse {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        assignees = assignees == null ? List.of() : List.copyOf(assignees);
    }
}
