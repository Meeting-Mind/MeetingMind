package com.meetingmind.demo.dto;

import java.time.Instant;

public record ProjectKnowledgeDetailResponse(
        String id,
        String spaceId,
        String type,
        String title,
        String content,
        String sourceMeetingId,
        String embeddingStatus,
        Instant updatedAt
) {
}
