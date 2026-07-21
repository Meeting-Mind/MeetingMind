package com.meetingmind.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "domain_terms")
public class DomainTerm {
    @Id String id;
    @Column(name = "space_id", nullable = false) String spaceId;
    @Column(nullable = false) String term;
    @Column(nullable = false) String definition;
    @Column(nullable = false) String status;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;
    @Column(name = "archived_at") Instant archivedAt;

    protected DomainTerm() {
    }

    public DomainTerm(
            String id,
            String spaceId,
            String term,
            String definition,
            DomainTermStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant archivedAt
    ) {
        this.id = id;
        this.spaceId = spaceId;
        this.term = term;
        this.definition = definition;
        this.status = status.name();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.archivedAt = archivedAt;
    }

    public String id() { return id; }
    public String spaceId() { return spaceId; }
    public String term() { return term; }
    public String definition() { return definition; }
    public DomainTermStatus status() { return DomainTermStatus.valueOf(status); }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant archivedAt() { return archivedAt; }

    public DomainTerm updated(String term, String definition, DomainTermStatus status, Instant now) {
        return new DomainTerm(
                id,
                spaceId,
                term,
                definition,
                status,
                createdAt,
                now,
                status == DomainTermStatus.ARCHIVED ? now : null
        );
    }

    @Override public boolean equals(Object other) { return other instanceof DomainTerm value && Objects.equals(id, value.id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
