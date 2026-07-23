package com.meetingmind.stt.dto;

public record TranscriptionStartResponse(String sessionId, String meetingId, String status, String egressId) {
}
