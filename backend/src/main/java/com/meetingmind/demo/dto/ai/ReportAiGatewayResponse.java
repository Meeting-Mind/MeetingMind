package com.meetingmind.demo.dto.ai;

import java.util.List;

public record ReportAiGatewayResponse(
        int schemaVersion,
        List<SummarySentence> summary,
        List<Decision> decisions,
        List<ActionItem> actionItems,
        List<Source> sources,
        int droppedCount,
        boolean unsupported,
        String unsupportedReason,
        String model,
        String generationMode,
        boolean degraded,
        List<String> warnings,
        int attemptCount,
        AiChatResponse.AiUsageMetrics usage
) {
    public ReportAiGatewayResponse {
        summary = summary == null ? List.of() : List.copyOf(summary);
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
        actionItems = actionItems == null ? List.of() : List.copyOf(actionItems);
        sources = sources == null ? List.of() : List.copyOf(sources);
        generationMode = generationMode == null || generationMode.isBlank() ? "AI_DIRECT" : generationMode;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        attemptCount = Math.max(1, attemptCount);
    }

    public ReportAiGatewayResponse(
            int schemaVersion,
            List<SummarySentence> summary,
            List<Decision> decisions,
            List<ActionItem> actionItems,
            List<Source> sources,
            int droppedCount,
            boolean unsupported,
            String unsupportedReason,
            String model,
            AiChatResponse.AiUsageMetrics usage
    ) {
        this(schemaVersion, summary, decisions, actionItems, sources, droppedCount, unsupported,
                unsupportedReason, model, "AI_DIRECT", false, List.of(), 1, usage);
    }

    public record SummarySentence(String text, List<String> sourceIds) {
        public SummarySentence {
            sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
        }
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
