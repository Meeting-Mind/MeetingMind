package com.meetingmind.demo.domain;

import java.time.Instant;
import java.time.LocalDate;

public record TaskCard(
        String id,
        String spaceId,
        String meetingId,
        String sourceCandidateId,
        String title,
        String description,
        TaskCardStatus status,
        String assigneeId,
        LocalDate dueDate,
        Instant createdAt,
        Instant updatedAt
) {
}
