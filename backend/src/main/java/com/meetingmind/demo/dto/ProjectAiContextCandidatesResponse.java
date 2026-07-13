package com.meetingmind.demo.dto;

import java.util.List;

public record ProjectAiContextCandidatesResponse(
        List<ProjectKnowledgeCandidate> projectKnowledge,
        List<MeetingCandidate> meetings
) {
    public record ProjectKnowledgeCandidate(String sourceId, String title, String text) {
    }

    public record MeetingCandidate(String meetingId, String title, String summary) {
    }
}
