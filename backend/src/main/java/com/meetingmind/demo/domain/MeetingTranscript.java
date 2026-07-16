package com.meetingmind.demo.domain;

import java.time.Instant;

public record MeetingTranscript(
        String meetingId,
        TranscriptStatus status,
        String provider,
        String language,
        Instant startedAt,
        Instant completedAt,
        String failureReason,
        Instant retentionUntil,
        boolean legalHold,
        Instant purgedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
