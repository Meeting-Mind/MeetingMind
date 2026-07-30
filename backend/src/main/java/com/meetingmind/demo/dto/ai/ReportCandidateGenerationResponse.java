package com.meetingmind.demo.dto.ai;

import java.time.Instant;
import java.util.List;

public record ReportCandidateGenerationResponse(
        Candidate candidate,
        List<ReportAiGatewayResponse.Source> sources,
        boolean unsupported,
        String unsupportedReason,
        int droppedCount,
        String model,
        String generationMode,
        boolean degraded,
        List<String> warnings,
        int attemptCount
) {
    public ReportCandidateGenerationResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
        generationMode = generationMode == null || generationMode.isBlank() ? "AI_DIRECT" : generationMode;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        attemptCount = Math.max(1, attemptCount);
    }

    public ReportCandidateGenerationResponse(
            Candidate candidate,
            List<ReportAiGatewayResponse.Source> sources,
            boolean unsupported,
            String unsupportedReason,
            int droppedCount,
            String model
    ) {
        this(candidate, sources, unsupported, unsupportedReason, droppedCount, model,
                "AI_DIRECT", false, List.of(), 1);
    }

    public record Candidate(
            String id,
            String meetingId,
            String status,
            String title,
            String summary,
            String markdown,
            List<Decision> decisions,
            List<ActionItem> actionItems,
            List<String> sourceIds,
            String createdBy,
            int version,
            boolean isCurrent,
            Instant createdAt
    ) {
        public Candidate {
            decisions = decisions == null ? List.of() : List.copyOf(decisions);
            actionItems = actionItems == null ? List.of() : List.copyOf(actionItems);
            sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
        }
    }

    public record Decision(String id, String title, String rationale, List<String> sourceIds) {
        public Decision {
            sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
        }
    }

    public record ActionItem(
            String id,
            String title,
            String assignee,
            String dueDate,
            String confirmationState,
            List<String> sourceIds
    ) {
        public ActionItem {
            sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
        }
    }
}
