package com.meetingmind.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "spaces")
public class Space {
    @Id String id;
    @Column(nullable = false) String name;
    String description;
    @Column(name = "created_by", nullable = false) String createdBy;
    @Column(name = "deleted_at") Instant deletedAt;
    @Column(name = "created_at", nullable = false) Instant createdAt;

    protected Space() {
    }

    public Space(String id, String name, String description, String createdBy, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public String createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }

    @Override public boolean equals(Object other) { return other instanceof Space value && Objects.equals(id, value.id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
