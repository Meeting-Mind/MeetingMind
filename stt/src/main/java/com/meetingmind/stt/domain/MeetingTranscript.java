package com.meetingmind.stt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "meeting_transcripts")
public class MeetingTranscript {
    @Id @Column(name = "meeting_id") String meetingId;
    @Column(nullable = false) String status;
    String provider;
    String language;
    @Column(name = "started_at") Instant startedAt;
    @Column(name = "completed_at") Instant completedAt;
    @Column(name = "failure_reason") String failureReason;
    @Column(name = "retention_until") Instant retentionUntil;
    @Column(name = "legal_hold", nullable = false) boolean legalHold;
    @Column(name = "purged_at") Instant purgedAt;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    protected MeetingTranscript() {
    }

    public MeetingTranscript(String meetingId, TranscriptStatus status, String provider, String language,
                             Instant startedAt, Instant completedAt, String failureReason, Instant retentionUntil,
                             boolean legalHold, Instant purgedAt, Instant createdAt, Instant updatedAt) {
        this.meetingId = meetingId;
        this.status = status.name();
        this.provider = provider;
        this.language = language;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.failureReason = failureReason;
        this.retentionUntil = retentionUntil;
        this.legalHold = legalHold;
        this.purgedAt = purgedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String meetingId() { return meetingId; }
    public TranscriptStatus status() { return TranscriptStatus.valueOf(status); }
    public String provider() { return provider; }
    public String language() { return language; }
    public Instant startedAt() { return startedAt; }
    public Instant completedAt() { return completedAt; }
    public String failureReason() { return failureReason; }
    public Instant retentionUntil() { return retentionUntil; }
    public boolean legalHold() { return legalHold; }
    public Instant purgedAt() { return purgedAt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    @Override public boolean equals(Object other) { return other instanceof MeetingTranscript value && Objects.equals(meetingId, value.meetingId); }
    @Override public int hashCode() { return Objects.hashCode(meetingId); }
}
