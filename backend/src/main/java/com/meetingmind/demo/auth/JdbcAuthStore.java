package com.meetingmind.demo.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "db"})
public class JdbcAuthStore implements AuthStore {

    private static final RowMapper<AuthUser> USER_MAPPER = JdbcAuthStore::mapUser;
    private static final RowMapper<AuthIdentity> IDENTITY_MAPPER = JdbcAuthStore::mapIdentity;
    private static final RowMapper<RefreshTokenSession> SESSION_MAPPER = JdbcAuthStore::mapSession;

    private final JdbcTemplate jdbc;

    public JdbcAuthStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AuthUser> findUserById(String userId) {
        return first(jdbc.query(
                """
                select id, email, display_name, picture_url, status, created_at, last_login_at
                from users
                where id = ?
                """,
                USER_MAPPER,
                userId
        ));
    }

    @Override
    public Optional<AuthUser> findUserByEmail(String email) {
        return first(jdbc.query(
                """
                select id, email, display_name, picture_url, status, created_at, last_login_at
                from users
                where lower(email) = ?
                """,
                USER_MAPPER,
                AuthStore.normalizeEmail(email)
        ));
    }

    @Override
    public Optional<AuthIdentity> findIdentity(String provider, String providerUserId) {
        return first(jdbc.query(
                """
                select id, user_id, provider, provider_user_id, password_hash, created_at, last_used_at
                from auth_identities
                where provider = ? and provider_user_id = ?
                """,
                IDENTITY_MAPPER,
                provider,
                providerUserId
        ));
    }

    @Override
    public AuthUser createUser(String email, String displayName, String pictureUrl, Instant now) {
        return createUserWithId("user-" + UUID.randomUUID(), email, displayName, pictureUrl, now);
    }

    @Override
    public AuthUser createUserWithId(String userId, String email, String displayName, String pictureUrl, Instant now) {
        AuthUser user = new AuthUser(
                userId,
                AuthStore.normalizeEmail(email),
                displayName,
                pictureUrl,
                "active",
                now,
                now
        );
        jdbc.update(
                """
                insert into users (id, email, display_name, picture_url, status, created_at, last_login_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                user.id(),
                user.email(),
                user.displayName(),
                user.pictureUrl(),
                user.status(),
                timestamp(user.createdAt()),
                timestamp(user.lastLoginAt())
        );
        return user;
    }

    @Override
    public AuthUser updateUserProfile(String userId, String displayName, String pictureUrl, Instant now) {
        AuthUser current = findUserById(userId).orElseThrow();
        if (jdbc.update(
                "update users set display_name = ?, picture_url = ? where id = ?",
                displayName,
                pictureUrl,
                userId) != 1) {
            throw new IllegalStateException("사용자 프로필을 갱신하지 못했습니다.");
        }
        return new AuthUser(
                current.id(),
                current.email(),
                displayName,
                pictureUrl,
                current.status(),
                current.createdAt(),
                current.lastLoginAt());
    }

    @Override
    public AuthUser touchLogin(AuthUser user, Instant now) {
        jdbc.update(
                "update users set last_login_at = ? where id = ?",
                timestamp(now),
                user.id()
        );
        return new AuthUser(
                user.id(),
                user.email(),
                user.displayName(),
                user.pictureUrl(),
                user.status(),
                user.createdAt(),
                now
        );
    }

    @Override
    public AuthIdentity saveIdentity(
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
        jdbc.update(
                """
                insert into auth_identities (
                    id, user_id, provider, provider_user_id, password_hash, created_at, last_used_at
                ) values (?, ?, ?, ?, ?, ?, ?)
                """,
                identity.id(),
                identity.userId(),
                identity.provider(),
                identity.providerUserId(),
                identity.passwordHash(),
                timestamp(identity.createdAt()),
                timestamp(identity.lastUsedAt())
        );
        return identity;
    }

    @Override
    public AuthIdentity touchIdentity(AuthIdentity identity, Instant now) {
        jdbc.update(
                "update auth_identities set last_used_at = ? where id = ?",
                timestamp(now),
                identity.id()
        );
        return new AuthIdentity(
                identity.id(),
                identity.userId(),
                identity.provider(),
                identity.providerUserId(),
                identity.passwordHash(),
                identity.createdAt(),
                now
        );
    }

    @Override
    public RefreshTokenSession saveRefreshSession(
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
        jdbc.update(
                """
                insert into auth_sessions (
                    id, user_id, refresh_token_hash, issued_at, expires_at, revoked_at, user_agent
                ) values (?, ?, ?, ?, ?, ?, ?)
                """,
                session.id(),
                session.userId(),
                session.refreshTokenHash(),
                timestamp(session.issuedAt()),
                timestamp(session.expiresAt()),
                null,
                session.userAgent()
        );
        return session;
    }

    @Override
    public Optional<RefreshTokenSession> findRefreshSessionForUpdate(String refreshTokenHash) {
        return first(jdbc.query(
                """
                select id, user_id, refresh_token_hash, issued_at, expires_at, revoked_at, user_agent
                from auth_sessions
                where refresh_token_hash = ?
                for update
                """,
                SESSION_MAPPER,
                refreshTokenHash
        ));
    }

    @Override
    public void revokeRefreshSession(String refreshTokenHash, Instant revokedAt) {
        jdbc.update(
                "update auth_sessions set revoked_at = ? where refresh_token_hash = ? and revoked_at is null",
                timestamp(revokedAt),
                refreshTokenHash
        );
    }

    private static AuthUser mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new AuthUser(
                rs.getString("id"),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getString("picture_url"),
                rs.getString("status"),
                instant(rs, "created_at"),
                nullableInstant(rs, "last_login_at")
        );
    }

    private static AuthIdentity mapIdentity(ResultSet rs, int rowNum) throws SQLException {
        return new AuthIdentity(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("provider"),
                rs.getString("provider_user_id"),
                rs.getString("password_hash"),
                instant(rs, "created_at"),
                nullableInstant(rs, "last_used_at")
        );
    }

    private static RefreshTokenSession mapSession(ResultSet rs, int rowNum) throws SQLException {
        return new RefreshTokenSession(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("refresh_token_hash"),
                instant(rs, "issued_at"),
                instant(rs, "expires_at"),
                nullableInstant(rs, "revoked_at"),
                rs.getString("user_agent")
        );
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    private static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static <T> Optional<T> first(List<T> values) {
        return values.stream().findFirst();
    }
}
