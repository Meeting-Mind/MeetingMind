package com.meetingmind.demo.dto;

import java.time.Instant;

public record ProjectKnowledgeMutationResponse(
        String id,
        String status,
        String embeddingStatus,
        String embeddingJobId,
        Instant updatedAt
) {
}
