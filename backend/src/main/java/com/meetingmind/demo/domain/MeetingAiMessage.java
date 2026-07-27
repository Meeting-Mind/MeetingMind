package com.meetingmind.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "meeting_ai_messages")
public class MeetingAiMessage {
    @Id String id;
    @Column(name = "meeting_id", nullable = false) String meetingId;
    @Column(name = "user_id", nullable = false) String userId;
    @Column(nullable = false) String role;
    @Column(nullable = false) String content;
    @Column(name = "created_at", nullable = false) Instant createdAt;

    protected MeetingAiMessage() { }

    public MeetingAiMessage(
            String id,
            String meetingId,
            String userId,
            String role,
            String content,
            Instant createdAt
    ) {
        this.id = id;
        this.meetingId = meetingId;
        this.userId = userId;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }

    public String id() { return id; }
    public String meetingId() { return meetingId; }
    public String userId() { return userId; }
    public String role() { return role; }
    public String content() { return content; }
    public Instant createdAt() { return createdAt; }
}
