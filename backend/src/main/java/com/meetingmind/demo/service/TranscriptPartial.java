package com.meetingmind.demo.service;

public record TranscriptPartial(
        String partialId,
        String sessionId,
        String trackId,
        String text,
        long startedAtMs,
        long updatedAtMs
) {
}
