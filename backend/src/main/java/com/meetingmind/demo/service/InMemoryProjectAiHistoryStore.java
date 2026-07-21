package com.meetingmind.demo.service;

import com.meetingmind.demo.domain.ProjectAiMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("test")
public class InMemoryProjectAiHistoryStore implements ProjectAiHistoryStore {
    private final List<ProjectAiMessage> messages = new ArrayList<>();

    @Override
    public synchronized List<ProjectAiMessage> find(String spaceId, String userId, int limit) {
        return messages.stream().filter(message -> message.spaceId().equals(spaceId) && message.userId().equals(userId))
                .sorted(Comparator.comparing(ProjectAiMessage::createdAt).reversed()).limit(limit)
                .sorted(Comparator.comparing(ProjectAiMessage::createdAt)).toList();
    }

    @Override
    public synchronized void append(String spaceId, String userId, String role, String content, Instant createdAt) {
        messages.add(new ProjectAiMessage("project-ai-message-" + UUID.randomUUID(), spaceId, userId, role, content, createdAt));
    }
}
