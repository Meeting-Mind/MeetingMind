package com.meetingmind.demo.domain;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!local & !db")
class InMemoryKnowledgeGraphEdgeStore implements KnowledgeGraphEdgeStore {

    @Override
    public List<StoredEdge> findBySpaceId(String spaceId) {
        return List.of();
    }
}
