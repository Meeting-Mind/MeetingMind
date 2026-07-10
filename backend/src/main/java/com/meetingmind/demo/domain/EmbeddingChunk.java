package com.meetingmind.demo.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record EmbeddingChunk(
        String id,
        String spaceId,
        String projectId,
        String meetingId,
        EmbeddingScope scope,
        SourceType sourceType,
        String sourceId,
        List<String> sourceSegmentIds,
        String title,
        List<String> speakerNames,
        Integer startMs,
        Integer endMs,
        String content,
        String embeddingText,
        Map<String, String> metadata,
        List<Double> embedding,
        Instant createdAt
) {
    public EmbeddingChunk {
        sourceSegmentIds = sourceSegmentIds == null ? List.of() : List.copyOf(sourceSegmentIds);
        speakerNames = speakerNames == null ? List.of() : List.copyOf(speakerNames);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        embedding = embedding == null ? List.of() : List.copyOf(embedding);
    }
}
