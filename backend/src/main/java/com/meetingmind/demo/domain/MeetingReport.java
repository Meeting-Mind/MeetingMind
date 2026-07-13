package com.meetingmind.demo.domain;

import java.time.Instant;
import java.util.List;

public record MeetingReport(
        String id,
        String meetingId,
        MeetingReportStatus status,
        String title,
        String summary,
        String markdown,
        List<ReportDecision> decisions,
        List<ReportActionItem> actionItems,
        List<String> sourceIds,
        String createdBy,
        int version,
        boolean current,
        Instant createdAt
) {
    public MeetingReport {
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
        actionItems = actionItems == null ? List.of() : List.copyOf(actionItems);
        sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
    }

    public record ReportDecision(String id, String title, String content, List<String> sourceIds) {
        public ReportDecision {
            sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
        }
    }

    public record ReportActionItem(String id, String title, String assigneeName, String dueDate, List<String> sourceIds) {
        public ReportActionItem {
            sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
        }
    }
}
