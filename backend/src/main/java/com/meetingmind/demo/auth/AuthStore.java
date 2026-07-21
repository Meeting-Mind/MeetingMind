package com.meetingmind.demo.auth;

import java.time.Instant;
import java.util.Optional;

public interface AuthStore {

    Optional<AuthUser> findUserById(String userId);

    Optional<AuthUser> findUserByEmail(String email);

    Optional<AuthIdentity> findIdentity(String provider, String providerUserId);

    AuthUser createUser(String email, String displayName, String pictureUrl, Instant now);

    AuthUser createUserWithId(String userId, String email, String displayName, String pictureUrl, Instant now);

    AuthUser updateUserProfile(String userId, String displayName, String pictureUrl, Instant now);

    AuthUser touchLogin(AuthUser user, Instant now);

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

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
