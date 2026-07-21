package com.meetingmind.auth.runtime;

import java.time.Instant;
import java.util.UUID;

final class AuthModels {

    private AuthModels() {
    }

    record User(
            UUID id,
            String email,
            String displayName,
            String pictureUrl,
            String status,
            Instant createdAt,
            Instant updatedAt,
            Instant lastLoginAt
    ) {
        boolean isActive() {
            return "ACTIVE".equals(status);
        }
    }

    record Identity(
            UUID id,
            UUID userId,
            String provider,
            String providerUserId,
            String passwordHash,
            Instant createdAt,
            Instant lastUsedAt
    ) {
    }

    record Session(
            UUID id,
            UUID userId,
            UUID refreshFamilyId,
            Instant createdAt,
            Instant lastRotatedAt,
            Instant expiresAt,
            Instant revokedAt,
            String revokeReason,
            String deviceLabel
    ) {
        boolean isRevoked() {
            return revokedAt != null;
        }

        boolean isExpired(Instant now) {
            return !expiresAt.isAfter(now);
        }
    }

    record Credential(
            UUID id,
            UUID authSessionId,
            UUID familyId,
            String tokenHash,
            Instant issuedAt,
            Instant expiresAt,
            Instant usedAt,
            Instant revokedAt,
            UUID replacementId
    ) {
        boolean isUsed() {
            return usedAt != null;
        }

        boolean isRevoked() {
            return revokedAt != null;
        }

        boolean isExpired(Instant now) {
            return !expiresAt.isAfter(now);
        }
    }

    record RefreshState(Credential credential, Session session, User user) {
    }

    record PasswordResetToken(
            UUID id,
            UUID userId,
            String tokenHash,
            String requestIpPrefix,
            Instant createdAt,
            Instant expiresAt,
            Instant usedAt
    ) {
        boolean isUsable(Instant now) {
            return usedAt == null && expiresAt.isAfter(now);
        }
    }

    record PasswordResetState(PasswordResetToken token, User user) {
    }

    record GoogleUser(
            String providerUserId,
            String email,
            String displayName,
            String pictureUrl
    ) {
    }
}
