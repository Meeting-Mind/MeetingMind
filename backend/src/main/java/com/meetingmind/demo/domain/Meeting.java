package com.meetingmind.demo.domain;

import com.meetingmind.demo.authz.MeetingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "meetings")
public class Meeting {
    @Id String id;
    @Column(name = "space_id", nullable = false) String spaceId;
    @Column(nullable = false) String title;
    @Column(name = "scheduled_at") OffsetDateTime scheduledAt;
    @Transient String joinCode;
    @Column(name = "started_at") OffsetDateTime startedAt;
    @Column(name = "ended_at") OffsetDateTime endedAt;
    @Column(nullable = false) String status;
    @Column(name = "failure_reason") String failureReason;
    @Column(name = "retention_policy", nullable = false) String retentionPolicy;
    @Column(name = "join_code_hash") String joinCodeHash;
    @Column(name = "deleted_at") Instant deletedAt;
    @Column(name = "deleted_by") String deletedBy;

    protected Meeting() {
    }

    public Meeting(String id, String spaceId, String title, OffsetDateTime scheduledAt, String joinCode,
                   OffsetDateTime startedAt, OffsetDateTime endedAt, MeetingStatus status, String failureReason,
                   String retentionPolicy, Instant deletedAt, String deletedBy) {
        this.id = id;
        this.spaceId = spaceId;
        this.title = title;
        this.scheduledAt = scheduledAt;
        this.joinCode = joinCode;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.status = status.name();
        this.failureReason = failureReason;
        this.retentionPolicy = retentionPolicy;
        this.deletedAt = deletedAt;
        this.deletedBy = deletedBy;
    }

    static Meeting scheduled(String id, String spaceId, String title, OffsetDateTime scheduledAt) {
        return new Meeting(id, spaceId, title, scheduledAt, UUID.randomUUID().toString().replace("-", ""),
                null, null, MeetingStatus.SCHEDULED, null, "DAYS_30", null, null);
    }

    public String id() { return id; }
    public String spaceId() { return spaceId; }
    public String title() { return title; }
    public OffsetDateTime scheduledAt() { return scheduledAt; }
    public String joinCode() { return joinCode; }
    public OffsetDateTime startedAt() { return startedAt; }
    public OffsetDateTime endedAt() { return endedAt; }
    public MeetingStatus status() { return MeetingStatus.valueOf(status); }
    public String failureReason() { return failureReason; }
    public String retentionPolicy() { return retentionPolicy; }
    public Instant deletedAt() { return deletedAt; }
    public String deletedBy() { return deletedBy; }
    boolean deleted() { return deletedAt != null; }

    @Override public boolean equals(Object other) { return other instanceof Meeting value && Objects.equals(id, value.id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
