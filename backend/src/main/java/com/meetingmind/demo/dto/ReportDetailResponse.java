package com.meetingmind.demo.dto;

import java.time.Instant;
import java.util.List;

/**
 * 저장된 회의록 상세.
 *
 * <p>{@code decisions}와 {@code actionItems}는 이미 `report_decisions` / `report_action_items`에
 * 저장되어 있고 도메인도 읽고 있었으나 이 응답에서만 빠져 있었다. 그래서 화면은 마크다운을
 * 정규식으로 훑어 결정 목록을 만들고 있었고, 할 일은 아예 보여주지 못했다.
 */
public record ReportDetailResponse(
        String id,
        String meetingId,
        String status,
        String title,
        String summary,
        String markdown,
        int version,
        boolean isCurrent,
        Instant createdAt,
        Instant confirmedAt,
        List<Decision> decisions,
        List<ActionItem> actionItems,
        List<String> sourceIds
) {
    public record Decision(String id, String title, String rationale, List<String> sourceIds) {
        public Decision {
            sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
        }
    }

    public record ActionItem(String id, String title, String assignee, String dueDate, List<String> sourceIds) {
        public ActionItem {
            sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
        }
    }
}
