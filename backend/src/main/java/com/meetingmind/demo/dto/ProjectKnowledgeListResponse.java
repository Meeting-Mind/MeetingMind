package com.meetingmind.demo.dto;

import java.time.Instant;
import java.util.List;

public record ProjectKnowledgeListResponse(List<Item> items) {
    public record Item(
            String id,
            String spaceId,
            String type,
            String title,
            String contentPreview,
            String sourceMeetingId,
            String embeddingStatus,
            Instant updatedAt
    ) {
    }
}
