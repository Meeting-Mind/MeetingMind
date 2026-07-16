package com.meetingmind.demo.dto;

public record MeetingTranscriptionStartResponse(
        String meetingId,
        String transcriptStatus,
        String sessionId,
        String egressId
) {
}
