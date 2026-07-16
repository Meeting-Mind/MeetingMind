package com.meetingmind.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "meeting_speakers")
public class MeetingSpeaker {
    @Id String id;
    @Column(name = "meeting_id", nullable = false) String meetingId;
    @Column(nullable = false) String label;
    @Column(name = "display_name") String displayName;
    @Column(name = "created_at", nullable = false) Instant createdAt;

    protected MeetingSpeaker() {
    }

    public MeetingSpeaker(String id, String meetingId, String label, String displayName, Instant createdAt) {
        this.id = id;
        this.meetingId = meetingId;
        this.label = label;
        this.displayName = displayName;
        this.createdAt = createdAt;
    }

    public String id() { return id; }
    public String meetingId() { return meetingId; }
    public String label() { return label; }
    public String displayName() { return displayName; }
    public Instant createdAt() { return createdAt; }

    @Override public boolean equals(Object other) { return other instanceof MeetingSpeaker value && Objects.equals(id, value.id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
