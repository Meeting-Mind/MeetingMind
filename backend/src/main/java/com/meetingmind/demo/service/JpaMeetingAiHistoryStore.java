package com.meetingmind.demo.service;

import com.meetingmind.demo.domain.MeetingAiMessage;
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
public class JpaMeetingAiHistoryStore implements MeetingAiHistoryStore {
    @PersistenceContext private EntityManager entityManager;

    @Override
    public List<MeetingAiMessage> find(String meetingId, String userId, int limit) {
        return entityManager.createQuery(
                        "select m from MeetingAiMessage m where m.meetingId = :meetingId and m.userId = :userId order by m.createdAt desc",
                        MeetingAiMessage.class
                )
                .setParameter("meetingId", meetingId)
                .setParameter("userId", userId)
                .setMaxResults(limit)
                .getResultList().stream()
                .sorted(Comparator.comparing(MeetingAiMessage::createdAt))
                .toList();
    }

    @Override
    @Transactional
    public void append(String meetingId, String userId, String role, String content, Instant createdAt) {
        entityManager.persist(new MeetingAiMessage(
                "meeting-ai-message-" + UUID.randomUUID(),
                meetingId,
                userId,
                role,
                content,
                createdAt
        ));
    }
}
