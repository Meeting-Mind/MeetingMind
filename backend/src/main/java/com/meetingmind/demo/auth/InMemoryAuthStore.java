package com.meetingmind.demo.auth;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryAuthStore {

    private final Map<String, AuthUser> usersById = new HashMap<>();
    private final Map<String, String> userIdByEmail = new HashMap<>();
    private final Map<String, AuthIdentity> identitiesByKey = new HashMap<>();
    private final Map<String, RefreshTokenSession> refreshSessionsByHash = new HashMap<>();

    synchronized Optional<AuthUser> findUserById(String userId) {
        return Optional.ofNullable(usersById.get(userId));
    }

    synchronized Optional<AuthUser> findUserByEmail(String email) {
        return Optional.ofNullable(userIdByEmail.get(normalizeEmail(email))).map(usersById::get);
    }

    synchronized Optional<AuthIdentity> findIdentity(String provider, String providerUserId) {
        return Optional.ofNullable(identitiesByKey.get(identityKey(provider, providerUserId)));
    }

    synchronized AuthUser createUser(String email, String displayName, String pictureUrl, Instant now) {
        String userId = "user-" + UUID.randomUUID();
        AuthUser user = new AuthUser(
                userId,
                normalizeEmail(email),
                displayName,
                pictureUrl,
                "active",
                now,
                now
        );
        usersById.put(user.id(), user);
        userIdByEmail.put(user.email(), user.id());
        return user;
    }

    synchronized AuthUser touchLogin(AuthUser user, Instant now) {
        AuthUser updated = new AuthUser(
                user.id(),
                user.email(),
                user.displayName(),
                user.pictureUrl(),
                user.status(),
                user.createdAt(),
                now
        );
        usersById.put(updated.id(), updated);
        userIdByEmail.put(updated.email(), updated.id());
        return updated;
    }

    synchronized AuthIdentity saveIdentity(
            String userId,
            String provider,
            String providerUserId,
            String passwordHash,
            Instant now
    ) {
        AuthIdentity identity = new AuthIdentity(
                "identity-" + UUID.randomUUID(),
                userId,
                provider,
                providerUserId,
                passwordHash,
                now,
                now
        );
        identitiesByKey.put(identityKey(provider, providerUserId), identity);
        return identity;
    }

    synchronized AuthIdentity touchIdentity(AuthIdentity identity, Instant now) {
        AuthIdentity updated = new AuthIdentity(
                identity.id(),
                identity.userId(),
                identity.provider(),
                identity.providerUserId(),
                identity.passwordHash(),
                identity.createdAt(),
                now
        );
        identitiesByKey.put(identityKey(updated.provider(), updated.providerUserId()), updated);
        return updated;
    }

    synchronized RefreshTokenSession saveRefreshSession(
            String userId,
            String refreshTokenHash,
            Instant issuedAt,
            Instant expiresAt,
            String userAgent
    ) {
        RefreshTokenSession session = new RefreshTokenSession(
                "session-" + UUID.randomUUID(),
                userId,
                refreshTokenHash,
                issuedAt,
                expiresAt,
                null,
                userAgent
        );
        refreshSessionsByHash.put(refreshTokenHash, session);
        return session;
    }

    synchronized Optional<RefreshTokenSession> findRefreshSession(String refreshTokenHash) {
        return Optional.ofNullable(refreshSessionsByHash.get(refreshTokenHash));
    }

    synchronized void revokeRefreshSession(String refreshTokenHash, Instant revokedAt) {
        RefreshTokenSession session = refreshSessionsByHash.get(refreshTokenHash);
        if (session == null) {
            return;
        }

        refreshSessionsByHash.put(
                refreshTokenHash,
                new RefreshTokenSession(
                        session.id(),
                        session.userId(),
                        session.refreshTokenHash(),
                        session.issuedAt(),
                        session.expiresAt(),
                        revokedAt,
                        session.userAgent()
                )
        );
    }

    private static String identityKey(String provider, String providerUserId) {
        return provider + ":" + providerUserId;
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
