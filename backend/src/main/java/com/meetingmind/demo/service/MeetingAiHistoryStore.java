package com.meetingmind.demo.service;

import com.meetingmind.demo.domain.MeetingAiMessage;
import java.time.Instant;
import java.util.List;

public interface MeetingAiHistoryStore {
    List<MeetingAiMessage> find(String meetingId, String userId, int limit);
    void append(String meetingId, String userId, String role, String content, Instant createdAt);
}
