package com.meetingmind.demo.dto;

import java.time.Instant;
import java.util.List;

public record ProjectAiHistoryResponse(List<Message> messages) {
    public record Message(String id, String role, String content, Instant createdAt) { }
}
