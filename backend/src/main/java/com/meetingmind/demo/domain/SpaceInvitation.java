package com.meetingmind.demo.domain;

import com.meetingmind.demo.authz.SpaceRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "space_invitations")
public class SpaceInvitation {
    @Id String id;
    @Column(name = "space_id", nullable = false) String spaceId;
    @Column(nullable = false) String email;
    @Column(nullable = false) String role;
    @Column(nullable = false) String status;
    @Column(name = "token_hash", nullable = false) String tokenHash;
    @Column(name = "expires_at", nullable = false) Instant expiresAt;
    @Column(name = "accepted_at") Instant acceptedAt;
    @Column(name = "declined_at") Instant declinedAt;

    protected SpaceInvitation() {
    }

    public SpaceInvitation(
            String id,
            String spaceId,
            String email,
            SpaceRole role,
            InvitationStatus status,
            String tokenHash,
            Instant expiresAt,
            Instant acceptedAt,
            Instant declinedAt
    ) {
        this.id = id;
        this.spaceId = spaceId;
        this.email = email;
        this.role = role.name();
        this.status = status.name();
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.acceptedAt = acceptedAt;
        this.declinedAt = declinedAt;
    }

    public String id() { return id; }
    public String spaceId() { return spaceId; }
    public String email() { return email; }
    public SpaceRole role() { return SpaceRole.valueOf(role); }
    public InvitationStatus status() { return InvitationStatus.valueOf(status); }
    public String tokenHash() { return tokenHash; }
    public Instant expiresAt() { return expiresAt; }
    public Instant acceptedAt() { return acceptedAt; }
    public Instant declinedAt() { return declinedAt; }

    public SpaceInvitation accepted(Instant value) {
        return new SpaceInvitation(id, spaceId, email, role(), InvitationStatus.ACCEPTED, tokenHash, expiresAt, value, null);
    }

    public SpaceInvitation declined(Instant value) {
        return new SpaceInvitation(id, spaceId, email, role(), InvitationStatus.DECLINED, tokenHash, expiresAt, null, value);
    }

    public SpaceInvitation expired() {
        return new SpaceInvitation(id, spaceId, email, role(), InvitationStatus.EXPIRED, tokenHash, expiresAt, null, null);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SpaceInvitation value && Objects.equals(id, value.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
