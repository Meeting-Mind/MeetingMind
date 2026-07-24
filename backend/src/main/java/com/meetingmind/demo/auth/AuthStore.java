package com.meetingmind.demo.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthStore {

    Optional<AuthUser> findUserById(String userId);

    Optional<AuthUser> findUserByAuthUserId(UUID authUserId);

    Optional<AuthUser> findUserByEmail(String email);

    Optional<AuthIdentity> findIdentity(String provider, String providerUserId);

    AuthUser createUser(String email, String displayName, String pictureUrl, Instant now);

    AuthUser touchLogin(AuthUser user, Instant now);

    AuthUser updateProfile(AuthUser user, String displayName, String pictureUrl);

    AuthIdentity saveIdentity(
            String userId,
            String provider,
            String providerUserId,
            String passwordHash,
            Instant now
    );

    AuthIdentity touchIdentity(AuthIdentity identity, Instant now);

    RefreshTokenSession saveRefreshSession(
            String userId,
            String refreshTokenHash,
            Instant issuedAt,
            Instant expiresAt,
            String userAgent
    );

    Optional<RefreshTokenSession> findRefreshSessionForUpdate(String refreshTokenHash);

    void revokeRefreshSession(String refreshTokenHash, Instant revokedAt);

    AuthUser upsertAuthProjection(
            UUID authUserId,
            String resourceUserId,
            String email,
            String displayName,
            String pictureUrl,
            String status,
            Instant now);

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
