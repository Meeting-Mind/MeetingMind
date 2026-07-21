package com.meetingmind.demo.dto.ai;

import java.util.List;

public record ProjectAiGatewayChatRequest(
        String projectId,
        String question,
        List<String> allowedMeetingIds,
        List<HistoryTurn> history
) {
    public ProjectAiGatewayChatRequest {
        allowedMeetingIds = allowedMeetingIds == null ? List.of() : List.copyOf(allowedMeetingIds);
        history = history == null ? List.of() : List.copyOf(history);
    }

    public record HistoryTurn(String role, String content) { }
}
