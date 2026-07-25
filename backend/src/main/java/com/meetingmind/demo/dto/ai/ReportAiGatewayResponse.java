package com.meetingmind.demo.dto.ai;

import java.util.List;

public record ReportAiGatewayResponse(
        String summary,
        List<Decision> decisions,
        List<ActionItem> actionItems,
        String markdown,
        List<Source> sources,
        boolean unsupported,
        String model,
        AiChatResponse.AiUsageMetrics usage
) {
    public ReportAiGatewayResponse {
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
        actionItems = actionItems == null ? List.of() : List.copyOf(actionItems);
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public record Decision(String title, String rationale, List<String> sourceIds) {
        public Decision {
            sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
        }
    }

    public record ActionItem(
            String title,
            String assignee,
            String dueDate,
            List<String> sourceIds,
            String confirmationState
    ) {
        public ActionItem {
            sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
        }
    }

    public record Source(
            String sourceId,
            String type,
            String title,
            String speaker,
            String time,
            Integer startMs,
            Integer endMs,
            String text
    ) {
    }
}
