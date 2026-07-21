package com.meetingmind.demo.service;

import com.meetingmind.demo.domain.ProjectAiMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile({"local", "db"})
public class JpaProjectAiHistoryStore implements ProjectAiHistoryStore {
    @PersistenceContext private EntityManager entityManager;

    @Override
    public List<ProjectAiMessage> find(String spaceId, String userId, int limit) {
        return entityManager.createQuery("select m from ProjectAiMessage m where m.spaceId = :spaceId and m.userId = :userId order by m.createdAt desc", ProjectAiMessage.class)
                .setParameter("spaceId", spaceId).setParameter("userId", userId).setMaxResults(limit).getResultList().stream()
                .sorted(Comparator.comparing(ProjectAiMessage::createdAt)).toList();
    }

    @Override
    @Transactional
    public void append(String spaceId, String userId, String role, String content, Instant createdAt) {
        entityManager.persist(new ProjectAiMessage("project-ai-message-" + UUID.randomUUID(), spaceId, userId, role, content, createdAt));
    }
}
