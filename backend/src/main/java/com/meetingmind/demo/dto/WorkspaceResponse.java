package com.meetingmind.demo.dto;

import java.util.List;

public record WorkspaceResponse(
        WorkspaceHome workspaceHome,
        LiveMeeting liveMeeting,
        MeetingAi meetingAi,
        ProjectOverview projectOverview,
        ReportAgent reportAgent
) {
    public record Overview(String title, String subtitle, List<String> status) {}

    public record LabeledItem(String title, String meta) {}

    public record LinkItem(String label, String href) {}

    public record TranscriptRow(String time, String speaker, String text) {}

    public record Metric(String label, String value, String note) {}

    public record MeetingRow(String index, String title, String date, String state) {}

    public record ReportDecision(String item, String decision, String note) {}

    public record ChatMessage(String role, String text) {}

    public record WorkspaceSpace(
            String name,
            String members,
            String meetings,
            String updatedAt,
            String description,
            String href
    ) {}

    public record WorkspaceTodayMeeting(
            String title,
            String project,
            String time,
            String attendees,
            String note,
            String href
    ) {}

    public record MeetingAccessMember(
            String name,
            String role,
            String access,
            String note
    ) {}

    public record WorkspaceHome(
            Overview overview,
            WorkspaceTodayMeeting todayMeeting,
            List<WorkspaceSpace> spaces,
            List<LabeledItem> recent
    ) {}

    public record LiveMeeting(
            Overview overview,
            String roomCode,
            String startsAt,
            List<String> participants,
            List<LabeledItem> checklist,
            List<MeetingAccessMember> accessMembers
    ) {}

    public record MeetingAi(
            Overview overview,
            List<TranscriptRow> transcript,
            List<LabeledItem> decisions,
            List<LabeledItem> actions,
            List<ChatMessage> chat,
            List<LinkItem> suggestions
    ) {}

    public record ProjectOverview(
            Overview overview,
            List<Metric> metrics,
            String techStack,
            List<MeetingRow> meetings,
            List<LabeledItem> documents,
            List<LinkItem> questions
    ) {}

    public record ReportAgent(
            Overview overview,
            String reportTitle,
            String reportDate,
            List<ReportDecision> decisions,
            List<LabeledItem> changes,
            List<ChatMessage> chat
    ) {}
}
