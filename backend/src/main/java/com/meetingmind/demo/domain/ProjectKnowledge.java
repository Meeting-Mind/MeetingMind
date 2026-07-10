package com.meetingmind.demo.domain;

import java.time.Instant;

public record ProjectKnowledge(
        String id,
        String spaceId,
        KnowledgeType type,
        String title,
        String content,
        String sourceMeetingId,
        String approvedBy,
        KnowledgeStatus status,
        EmbeddingStatus embeddingStatus,
        String embeddingJobId,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) {
}
