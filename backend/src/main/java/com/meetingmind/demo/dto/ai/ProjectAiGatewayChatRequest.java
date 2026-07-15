package com.meetingmind.demo.dto.ai;

import java.util.List;

public record ProjectAiGatewayChatRequest(
        String projectId,
        String question,
        List<String> allowedMeetingIds
) {
    public ProjectAiGatewayChatRequest {
        allowedMeetingIds = allowedMeetingIds == null ? List.of() : List.copyOf(allowedMeetingIds);
    }
}
