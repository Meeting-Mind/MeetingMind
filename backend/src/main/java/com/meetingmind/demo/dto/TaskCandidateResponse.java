package com.meetingmind.demo.dto;

import com.meetingmind.demo.domain.TaskCandidate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TaskCandidateResponse(
        String id,
        String meetingId,
        String title,
        String assigneeName,
        String suggestedAssigneeId,
        LocalDate dueDate,
        String status,
        List<String> sourceIds,
        String createdBy,
        Instant createdAt
) {
    public static TaskCandidateResponse from(TaskCandidate candidate) {
        return new TaskCandidateResponse(
                candidate.id(),
                candidate.meetingId(),
                candidate.title(),
                candidate.assigneeName(),
                candidate.suggestedAssigneeId(),
                candidate.dueDate(),
                candidate.status().name(),
                candidate.sourceIds(),
                candidate.createdBy(),
                candidate.createdAt()
        );
    }
}
