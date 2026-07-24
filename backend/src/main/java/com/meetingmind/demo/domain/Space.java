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
    @Column(name = "image_url") String imageUrl;
    @Column(name = "created_by", nullable = false) String createdBy;
    @Column(name = "deleted_at") Instant deletedAt;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    protected Space() {
    }

    public Space(String id, String name, String description, String imageUrl, String createdBy, Instant createdAt) {
        this(id, name, description, imageUrl, createdBy, null, createdAt, createdAt);
    }

    private Space(
            String id,
            String name,
            String description,
            String imageUrl,
            String createdBy,
            Instant deletedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.createdBy = createdBy;
        this.deletedAt = deletedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public String imageUrl() { return imageUrl; }
    public String createdBy() { return createdBy; }
    public Instant deletedAt() { return deletedAt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public Space updated(String nextName, String nextDescription, String nextImageUrl, Instant now) {
        return new Space(id, nextName, nextDescription, nextImageUrl, createdBy, deletedAt, createdAt, now);
    }

    public Space deleted(Instant value) {
        return new Space(id, name, description, imageUrl, createdBy, value, createdAt, updatedAt);
    }

    @Override public boolean equals(Object other) { return other instanceof Space value && Objects.equals(id, value.id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
