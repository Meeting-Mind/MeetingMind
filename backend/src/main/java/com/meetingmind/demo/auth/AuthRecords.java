package com.meetingmind.demo.auth;

import java.time.Instant;

record AuthUser(
        String id,
        String email,
        String displayName,
        String pictureUrl,
        String status,
        Instant createdAt,
        Instant lastLoginAt
) {
}

record AuthIdentity(
        String id,
        String userId,
        String provider,
        String providerUserId,
        String passwordHash,
        Instant createdAt,
        Instant lastUsedAt
) {
}

record RefreshTokenSession(
        String id,
        String userId,
        String refreshTokenHash,
        Instant issuedAt,
        Instant expiresAt,
        Instant revokedAt,
        String userAgent
) {
    boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}

record GoogleUserInfo(
        String providerUserId,
        String email,
        String displayName,
        String pictureUrl
) {
}
