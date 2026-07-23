package com.meetingmind.stt.service;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

final class SttTranscriptEventMapper {

    private final String provider;
    private final SttSessionContext context;
    private final AtomicLong sequence = new AtomicLong();

    SttTranscriptEventMapper(String provider, SttSessionContext context) {
        this.provider = provider;
        this.context = context;
    }

    TranscriptEvent map(SttTranscriptChunk chunk) {
        TranscriptEventType type = chunk.finalChunk() ? TranscriptEventType.FINAL : TranscriptEventType.PARTIAL;
        String providerSegmentId = chunk.position() >= 0 ? "position-" + chunk.position() : null;
        return event(
                type,
                UUID.randomUUID().toString(),
                providerSegmentId,
                chunk.text(),
                chunk.startTimestamp(),
                chunk.endTimestamp() > 0 ? (long) chunk.endTimestamp() : null,
                chunk.finalChunk()
        );
    }

    TranscriptEvent event(
            TranscriptEventType type,
            String providerEventId,
            String providerSegmentId,
            String text,
            long startedAtMs,
            Long endedAtMs,
            boolean endpointDetected
    ) {
        return new TranscriptEvent(
                context.sessionId(),
                context.meetingId(),
                provider,
                providerEventId,
                providerSegmentId,
                context.participantId(),
                context.trackId(),
                type,
                TranscriptTextSanitizer.sanitize(text),
                sequence.incrementAndGet(),
                Math.max(0, startedAtMs),
                endedAtMs == null ? null : Math.max(Math.max(0, startedAtMs), endedAtMs),
                null,
                endpointDetected
        );
    }
}
