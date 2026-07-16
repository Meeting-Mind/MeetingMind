package com.meetingmind.demo.domain;

import com.meetingmind.demo.authz.SpaceRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "space_members")
public class SpaceMember {
    @Id String id;
    @Column(name = "space_id", nullable = false) String spaceId;
    @Column(name = "user_id", nullable = false) String userId;
    @Column(nullable = false) String role;
    @Column(name = "joined_at", nullable = false) Instant joinedAt;
    @Column(name = "removed_at") Instant removedAt;

    protected SpaceMember() {
    }

    public SpaceMember(String id, String spaceId, String userId, SpaceRole role, Instant joinedAt) {
        this.id = id;
        this.spaceId = spaceId;
        this.userId = userId;
        this.role = role.name();
        this.joinedAt = joinedAt;
    }

    public String id() { return id; }
    public String spaceId() { return spaceId; }
    public String userId() { return userId; }
    public SpaceRole role() { return SpaceRole.valueOf(role); }
    public Instant joinedAt() { return joinedAt; }

    @Override public boolean equals(Object other) { return other instanceof SpaceMember value && Objects.equals(id, value.id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
