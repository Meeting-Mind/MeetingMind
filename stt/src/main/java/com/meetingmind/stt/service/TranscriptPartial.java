package com.meetingmind.stt.service;

public record TranscriptPartial(
        String partialId,
        String sessionId,
        String trackId,
        String text,
        long startedAtMs,
        long updatedAtMs
) {
}
