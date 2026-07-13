package com.meetingmind.demo.dto.ai;

import java.util.List;

public record MeetingAiGatewayChatRequest(
        String projectId,
        String meetingId,
        String meetingTitle,
        String question,
        List<TranscriptRow> transcript,
        List<LabeledItem> decisions,
        List<LabeledItem> actions,
        List<SourceContext> sources
) {
    public MeetingAiGatewayChatRequest {
        transcript = transcript == null ? List.of() : List.copyOf(transcript);
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
        actions = actions == null ? List.of() : List.copyOf(actions);
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public record TranscriptRow(String time, String speaker, String text) {
    }

    public record LabeledItem(String title, String meta) {
    }

    public record SourceContext(
            String sourceId,
            String type,
            String meetingId,
            String title,
            String speaker,
            String time,
            Integer startMs,
            Integer endMs,
            String text
    ) {
    }
}
