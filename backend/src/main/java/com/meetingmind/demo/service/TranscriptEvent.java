package com.meetingmind.demo.service;

public record TranscriptEvent(
        String sessionId,
        String meetingId,
        String provider,
        String providerEventId,
        String providerSegmentId,
        String participantId,
        String trackId,
        TranscriptEventType type,
        String text,
        long sequence,
        long startedAtMs,
        Long endedAtMs,
        Double confidence,
        boolean endpointDetected
) {

    public boolean isFinal() {
        return type == TranscriptEventType.FINAL;
    }
}
