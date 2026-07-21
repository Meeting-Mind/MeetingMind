package com.meetingmind.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "project_ai_messages")
public class ProjectAiMessage {
    @Id String id;
    @Column(name = "space_id", nullable = false) String spaceId;
    @Column(name = "user_id", nullable = false) String userId;
    @Column(nullable = false) String role;
    @Column(nullable = false) String content;
    @Column(name = "created_at", nullable = false) Instant createdAt;

    protected ProjectAiMessage() { }

    public ProjectAiMessage(String id, String spaceId, String userId, String role, String content, Instant createdAt) {
        this.id = id; this.spaceId = spaceId; this.userId = userId; this.role = role; this.content = content; this.createdAt = createdAt;
    }

    public String id() { return id; }
    public String spaceId() { return spaceId; }
    public String userId() { return userId; }
    public String role() { return role; }
    public String content() { return content; }
    public Instant createdAt() { return createdAt; }
}
