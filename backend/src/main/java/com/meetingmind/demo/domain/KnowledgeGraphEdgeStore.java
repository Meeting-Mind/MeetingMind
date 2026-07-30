package com.meetingmind.demo.domain;

import java.util.List;

public interface KnowledgeGraphEdgeStore {

    List<StoredEdge> findBySpaceId(String spaceId);

    record StoredEdge(String id, String fromNodeId, String toNodeId, double similarity) {
    }
}
