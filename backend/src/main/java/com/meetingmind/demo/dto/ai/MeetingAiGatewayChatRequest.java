package com.meetingmind.demo.dto.ai;

import java.util.List;

public record MeetingAiGatewayChatRequest(
        String projectId,
        String meetingId,
        String question,
        List<HistoryTurn> history
) {
    public MeetingAiGatewayChatRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }

    public MeetingAiGatewayChatRequest(String projectId, String meetingId, String question) {
        this(projectId, meetingId, question, List.of());
    }

    public record HistoryTurn(String role, String content) { }
}
