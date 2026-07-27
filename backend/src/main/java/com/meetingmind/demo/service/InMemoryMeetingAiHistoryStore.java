package com.meetingmind.demo.service;

import com.meetingmind.demo.domain.MeetingAiMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("test")
public class InMemoryMeetingAiHistoryStore implements MeetingAiHistoryStore {
    private final List<MeetingAiMessage> messages = new ArrayList<>();

    @Override
    public synchronized List<MeetingAiMessage> find(String meetingId, String userId, int limit) {
        return messages.stream()
                .filter(message -> message.meetingId().equals(meetingId) && message.userId().equals(userId))
                .sorted(Comparator.comparing(MeetingAiMessage::createdAt).reversed())
                .limit(limit)
                .sorted(Comparator.comparing(MeetingAiMessage::createdAt))
                .toList();
    }

    @Override
    public synchronized void append(String meetingId, String userId, String role, String content, Instant createdAt) {
        messages.add(new MeetingAiMessage(
                "meeting-ai-message-" + UUID.randomUUID(),
                meetingId,
                userId,
                role,
                content,
                createdAt
        ));
    }
}
