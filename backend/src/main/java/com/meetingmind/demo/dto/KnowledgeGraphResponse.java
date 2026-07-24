package com.meetingmind.demo.dto;

import java.util.List;

public record KnowledgeGraphResponse(
        List<Cluster> clusters,
        List<Edge> edges,
        String generatedAt
) {
    public KnowledgeGraphResponse {
        clusters = clusters == null ? List.of() : List.copyOf(clusters);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }

    public record Cluster(String id, String label, int sourceCount, List<Node> nodes) {
        public Cluster {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
        }
    }

    public record Node(
            String id,
            String sourceType,
            String title,
            String sourceMeetingId,
            String embeddingStatus
    ) {
    }

    public record Edge(String from, String to, double similarity) {
    }
}
