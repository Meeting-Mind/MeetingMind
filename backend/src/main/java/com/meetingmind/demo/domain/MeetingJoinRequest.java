package com.meetingmind.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "meeting_join_requests")
public class MeetingJoinRequest {
    @Id String id;
    @Column(name = "meeting_id", nullable = false) String meetingId;
    @Column(name = "user_id", nullable = false) String userId;
    @Column(nullable = false) String status;
    @Column(name = "requested_at", nullable = false) Instant requestedAt;
    @Column(name = "reviewed_at") Instant reviewedAt;
    @Column(name = "reviewed_by") String reviewedBy;

    protected MeetingJoinRequest() {
    }

    public MeetingJoinRequest(String id, String meetingId, String userId, MeetingJoinRequestStatus status,
                              Instant requestedAt, Instant reviewedAt, String reviewedBy) {
        this.id = id;
        this.meetingId = meetingId;
        this.userId = userId;
        this.status = status.name();
        this.requestedAt = requestedAt;
        this.reviewedAt = reviewedAt;
        this.reviewedBy = reviewedBy;
    }

    public String id() { return id; }
    public String meetingId() { return meetingId; }
    public String userId() { return userId; }
    public MeetingJoinRequestStatus status() { return MeetingJoinRequestStatus.valueOf(status); }
    public Instant requestedAt() { return requestedAt; }
    public Instant reviewedAt() { return reviewedAt; }
    public String reviewedBy() { return reviewedBy; }

    @Override public boolean equals(Object other) { return other instanceof MeetingJoinRequest value && Objects.equals(id, value.id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
