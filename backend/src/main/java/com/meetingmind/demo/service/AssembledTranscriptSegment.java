package com.meetingmind.demo.service;

public record AssembledTranscriptSegment(
        String segmentId,
        String sessionId,
        String trackId,
        String provider,
        String providerEventId,
        String text,
        long startedAtMs,
        long endedAtMs
) {
}
