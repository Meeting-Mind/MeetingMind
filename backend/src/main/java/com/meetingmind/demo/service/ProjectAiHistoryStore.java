package com.meetingmind.demo.service;

import com.meetingmind.demo.domain.ProjectAiMessage;
import java.time.Instant;
import java.util.List;

public interface ProjectAiHistoryStore {
    List<ProjectAiMessage> find(String spaceId, String userId, int limit);
    void append(String spaceId, String userId, String role, String content, Instant createdAt);
}
