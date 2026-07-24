package com.meetingmind.demo.dto.ai;

import java.util.List;

public record KnowledgeGraphGatewayRequest(String projectId, List<String> allowedMeetingIds) {
    public KnowledgeGraphGatewayRequest {
        allowedMeetingIds = allowedMeetingIds == null ? List.of() : List.copyOf(allowedMeetingIds);
    }
}
