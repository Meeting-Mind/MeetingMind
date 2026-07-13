package com.meetingmind.demo.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TaskCandidate(
        String id,
        String meetingId,
        String title,
        String assigneeName,
        String suggestedAssigneeId,
        LocalDate dueDate,
        TaskCandidateStatus status,
        List<String> sourceIds,
        String createdBy,
        Instant createdAt,
        Instant confirmedAt
) {
    public TaskCandidate {
        sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
    }

    public TaskCandidate confirmed(Instant confirmedAt) {
        return new TaskCandidate(
                id, meetingId, title, assigneeName, suggestedAssigneeId, dueDate,
                TaskCandidateStatus.CONFIRMED, sourceIds, createdBy, createdAt, confirmedAt
        );
    }
}
