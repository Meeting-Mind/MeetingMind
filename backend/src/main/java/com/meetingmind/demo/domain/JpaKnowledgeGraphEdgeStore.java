package com.meetingmind.demo.domain;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "db"})
class JpaKnowledgeGraphEdgeStore implements KnowledgeGraphEdgeStore {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<StoredEdge> findBySpaceId(String spaceId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                select id, from_node_id, to_node_id, similarity
                from knowledge_graph_edges
                where space_id = :spaceId
                order by from_node_id, to_node_id, id
                """)
                .setParameter("spaceId", spaceId)
                .getResultList();
        return rows.stream()
                .map(row -> new StoredEdge(
                        (String) row[0],
                        (String) row[1],
                        (String) row[2],
                        ((Number) row[3]).doubleValue()
                ))
                .toList();
    }
}
