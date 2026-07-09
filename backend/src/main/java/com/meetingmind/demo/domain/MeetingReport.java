package com.meetingmind.demo.domain;

import java.time.Instant;
import java.util.List;

public record MeetingReport(
        String id,
        String meetingId,
        MeetingReportStatus status,
        String title,
        String summary,
        List<ReportDecision> decisions,
        List<ReportActionItem> actionItems,
        int version,
        boolean current,
        Instant createdAt
) {
    public MeetingReport {
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
        actionItems = actionItems == null ? List.of() : List.copyOf(actionItems);
    }

    public record ReportDecision(String id, String title, String content, List<String> sourceIds) {
        public ReportDecision {
            sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
        }
    }

    public record ReportActionItem(String id, String title, String assigneeId, String dueDate, List<String> sourceIds) {
        public ReportActionItem {
            sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
        }
    }
}
