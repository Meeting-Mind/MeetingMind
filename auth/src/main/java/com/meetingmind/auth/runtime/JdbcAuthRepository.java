package com.meetingmind.auth.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAuthRepository {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final RowMapper<AuthModels.User> USER_MAPPER = JdbcAuthRepository::mapUser;
    private static final RowMapper<AuthModels.Identity> IDENTITY_MAPPER = JdbcAuthRepository::mapIdentity;
    private static final RowMapper<AuthModels.Session> SESSION_MAPPER = JdbcAuthRepository::mapSession;

    private final JdbcTemplate jdbc;

    JdbcAuthRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<AuthModels.User> findUserById(UUID userId) {
        return first(jdbc.query("""
                select id, email, display_name, picture_url, status, created_at, updated_at, last_login_at
                from auth_users
                where id = ?
                """, USER_MAPPER, userId));
    }

    Optional<AuthModels.User> findUserByEmail(String email) {
        return first(jdbc.query("""
                select id, email, display_name, picture_url, status, created_at, updated_at, last_login_at
                from auth_users
                where email = ?
                """, USER_MAPPER, canonicalEmail(email)));
    }

    Optional<AuthModels.User> findUserByIdForUpdate(UUID userId) {
        return first(jdbc.query("""
                select id, email, display_name, picture_url, status, created_at, updated_at, last_login_at
                from auth_users
                where id = ?
                for update
                """, USER_MAPPER, userId));
    }

    Optional<AuthModels.Identity> findIdentity(String provider, String providerUserId) {
        return first(jdbc.query("""
                select id, user_id, provider, provider_user_id, password_hash, created_at, last_used_at
                from auth_identities
                where provider = ? and provider_user_id = ?
                """, IDENTITY_MAPPER, provider, providerUserId));
    }

    Optional<AuthModels.Identity> findIdentityForUserForUpdate(UUID userId, String provider) {
        return first(jdbc.query("""
                select id, user_id, provider, provider_user_id, password_hash, created_at, last_used_at
                from auth_identities
                where user_id = ? and provider = ?
                for update
                """, IDENTITY_MAPPER, userId, provider));
    }

    AuthModels.User insertUser(
            UUID userId,
            String email,
            String displayName,
            String pictureUrl,
            Instant now
    ) {
        return jdbc.queryForObject("""
                insert into auth_users (
                    id, email, display_name, picture_url, status, created_at, updated_at, last_login_at
                ) values (?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                returning id, email, display_name, picture_url, status, created_at, updated_at, last_login_at
                """, USER_MAPPER, userId, canonicalEmail(email), displayName, pictureUrl,
                timestamp(now), timestamp(now), timestamp(now));
    }

    AuthModels.User upsertGoogleUser(
            UUID userId,
            String email,
            String displayName,
            String pictureUrl,
            Instant now
    ) {
        return jdbc.queryForObject("""
                insert into auth_users (
                    id, email, display_name, picture_url, status, created_at, updated_at, last_login_at
                ) values (?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                on conflict (email) do update
                set picture_url = coalesce(auth_users.picture_url, excluded.picture_url),
                    updated_at = excluded.updated_at,
                    last_login_at = excluded.last_login_at
                returning id, email, display_name, picture_url, status, created_at, updated_at, last_login_at
                """, USER_MAPPER, userId, canonicalEmail(email), displayName, pictureUrl,
                timestamp(now), timestamp(now), timestamp(now));
    }

    AuthModels.Identity insertIdentity(
            UUID identityId,
            UUID userId,
            String provider,
            String providerUserId,
            String passwordHash,
            Instant now
    ) {
        return jdbc.queryForObject("""
                insert into auth_identities (
                    id, user_id, provider, provider_user_id, password_hash, created_at, last_used_at
                ) values (?, ?, ?, ?, ?, ?, ?)
                returning id, user_id, provider, provider_user_id, password_hash, created_at, last_used_at
                """, IDENTITY_MAPPER, identityId, userId, provider, providerUserId, passwordHash,
                timestamp(now), timestamp(now));
    }

    AuthModels.Identity insertGoogleIdentity(
            UUID identityId,
            UUID userId,
            String providerUserId,
            Instant now
    ) {
        return jdbc.queryForObject("""
                insert into auth_identities (
                    id, user_id, provider, provider_user_id, password_hash, created_at, last_used_at
                ) values (?, ?, 'GOOGLE', ?, null, ?, ?)
                returning id, user_id, provider, provider_user_id, password_hash, created_at, last_used_at
                """, IDENTITY_MAPPER, identityId, userId, providerUserId, timestamp(now), timestamp(now));
    }

    void lockGoogleSubject(String providerUserId) {
        jdbc.query(
                "select pg_advisory_xact_lock(hashtextextended(?, 0))",
                rows -> {
                    // PostgreSQL advisory lock 함수는 void를 반환하므로 결과값을 읽지 않는다.
                },
                "GOOGLE:" + providerUserId
        );
    }

    void touchIdentityAndUser(AuthModels.Identity identity, Instant now) {
        jdbc.update(
                "update auth_identities set last_used_at = ? where id = ?",
                timestamp(now),
                identity.id()
        );
        jdbc.update(
                "update auth_users set last_login_at = ?, updated_at = ? where id = ?",
                timestamp(now),
                timestamp(now),
                identity.userId()
        );
    }

    void touchUser(UUID userId, Instant now) {
        jdbc.update(
                "update auth_users set last_login_at = ?, updated_at = ? where id = ?",
                timestamp(now),
                timestamp(now),
                userId
        );
    }

    AuthModels.User updateProfile(UUID userId, String displayName, Instant now) {
        return jdbc.queryForObject("""
                update auth_users
                set display_name = ?, updated_at = ?
                where id = ? and status = 'ACTIVE'
                returning id, email, display_name, picture_url, status, created_at, updated_at, last_login_at
                """, USER_MAPPER, displayName.trim(), timestamp(now), userId);
    }

    AuthModels.User updateProfileImage(UUID userId, String pictureUrl, Instant now) {
        return jdbc.queryForObject("""
                update auth_users
                set picture_url = ?, updated_at = ?
                where id = ? and status = 'ACTIVE'
                returning id, email, display_name, picture_url, status, created_at, updated_at, last_login_at
                """, USER_MAPPER, pictureUrl, timestamp(now), userId);
    }

    boolean disableUser(UUID userId, Instant now) {
        return jdbc.update("""
                update auth_users
                set status = 'DISABLED', picture_url = null, disabled_at = ?, withdrawal_requested_at = ?, updated_at = ?
                where id = ? and status = 'ACTIVE'
                """, timestamp(now), timestamp(now), timestamp(now), userId) == 1;
    }

    void updateLocalPassword(UUID identityId, String passwordHash, Instant now) {
        if (jdbc.update("""
                update auth_identities
                set password_hash = ?, last_used_at = ?
                where id = ? and provider = 'LOCAL'
                """, passwordHash, timestamp(now), identityId) != 1) {
            throw new IllegalStateException("local credential을 갱신할 수 없습니다.");
        }
    }

    void insertPasswordHistory(UUID userId, String passwordHash, Instant now) {
        jdbc.update("""
                insert into auth_password_history (id, user_id, password_hash, created_at)
                values (?, ?, ?, ?)
                """, UUID.randomUUID(), userId, passwordHash, timestamp(now));
    }

    List<String> findRecentPasswordHashes(UUID userId, int limit) {
        return jdbc.query("""
                select password_hash
                from auth_password_history
                where user_id = ?
                order by created_at desc, id desc
                limit ?
                """, (rows, rowNumber) -> rows.getString(1), userId, limit);
    }

    int countPasswordResetRequestsForUser(UUID userId, Instant since) {
        Integer count = jdbc.queryForObject("""
                select count(*)
                from auth_password_reset_tokens
                where user_id = ? and created_at >= ?
                """, Integer.class, userId, timestamp(since));
        return count == null ? 0 : count;
    }

    void lockPasswordResetRateLimits(UUID userId, String requestIpPrefix) {
        advisoryLock("PASSWORD_RESET_ACCOUNT:" + userId);
        if (requestIpPrefix != null && !requestIpPrefix.isBlank()) {
            advisoryLock("PASSWORD_RESET_IP:" + requestIpPrefix);
        }
    }

    int countPasswordResetRequestsForIp(String requestIpPrefix, Instant since) {
        if (requestIpPrefix == null || requestIpPrefix.isBlank()) {
            return 0;
        }
        Integer count = jdbc.queryForObject("""
                select count(*)
                from auth_password_reset_tokens
                where request_ip_prefix = ? and created_at >= ?
                """, Integer.class, requestIpPrefix, timestamp(since));
        return count == null ? 0 : count;
    }

    void insertPasswordResetToken(
            UUID tokenId,
            UUID userId,
            String tokenHash,
            String requestIpPrefix,
            Instant now,
            Instant expiresAt
    ) {
        jdbc.update("""
                insert into auth_password_reset_tokens (
                    id, user_id, token_hash, request_ip_prefix, created_at, expires_at, used_at
                ) values (?, ?, ?, ?, ?, ?, null)
                """, tokenId, userId, tokenHash, emptyToNull(requestIpPrefix), timestamp(now), timestamp(expiresAt));
    }

    Optional<AuthModels.PasswordResetState> findPasswordResetStateForUpdate(String tokenHash) {
        return first(jdbc.query("""
                select
                    t.id as token_id,
                    t.user_id as token_user_id,
                    t.token_hash,
                    t.request_ip_prefix,
                    t.created_at as token_created_at,
                    t.expires_at as token_expires_at,
                    t.used_at,
                    u.id as user_id,
                    u.email,
                    u.display_name,
                    u.picture_url,
                    u.status,
                    u.created_at as user_created_at,
                    u.updated_at as user_updated_at,
                    u.last_login_at
                from auth_password_reset_tokens t
                join auth_users u on u.id = t.user_id
                where t.token_hash = ?
                for update of t, u
                """, JdbcAuthRepository::mapPasswordResetState, tokenHash));
    }

    boolean consumePasswordResetToken(UUID tokenId, Instant now) {
        return jdbc.update("""
                update auth_password_reset_tokens
                set used_at = ?
                where id = ? and used_at is null
                """, timestamp(now), tokenId) == 1;
    }

    void insertSessionAndCredential(
            UUID sessionId,
            UUID userId,
            UUID familyId,
            UUID credentialId,
            String tokenHash,
            Instant now,
            Instant expiresAt,
            String deviceLabel
    ) {
        jdbc.update("""
                insert into auth_sessions (
                    id, user_id, refresh_family_id, created_at, last_rotated_at, expires_at,
                    revoked_at, revoke_reason, device_label
                ) values (?, ?, ?, ?, ?, ?, null, null, ?)
                """, sessionId, userId, familyId, timestamp(now), timestamp(now),
                timestamp(expiresAt), deviceLabel);
        jdbc.update("""
                insert into auth_refresh_credentials (
                    id, auth_session_id, family_id, token_hash, issued_at, expires_at,
                    used_at, revoked_at, replacement_id
                ) values (?, ?, ?, ?, ?, ?, null, null, null)
                """, credentialId, sessionId, familyId, tokenHash, timestamp(now), timestamp(expiresAt));
    }

    Optional<AuthModels.RefreshState> findRefreshStateForUpdate(String tokenHash) {
        return first(jdbc.query("""
                select
                    c.id as credential_id,
                    c.auth_session_id,
                    c.family_id,
                    c.token_hash,
                    c.issued_at as credential_issued_at,
                    c.expires_at as credential_expires_at,
                    c.used_at,
                    c.revoked_at as credential_revoked_at,
                    c.replacement_id,
                    s.id as session_id,
                    s.user_id,
                    s.refresh_family_id,
                    s.created_at as session_created_at,
                    s.last_rotated_at,
                    s.expires_at as session_expires_at,
                    s.revoked_at as session_revoked_at,
                    s.revoke_reason,
                    s.device_label,
                    u.email,
                    u.display_name,
                    u.picture_url,
                    u.status,
                    u.created_at as user_created_at,
                    u.updated_at as user_updated_at,
                    u.last_login_at
                from auth_refresh_credentials c
                join auth_sessions s on s.id = c.auth_session_id
                join auth_users u on u.id = s.user_id
                where c.token_hash = ?
                for update of c, s
                """, JdbcAuthRepository::mapRefreshState, tokenHash));
    }

    Optional<AuthModels.Session> findSessionForUpdate(UUID sessionId) {
        return first(jdbc.query("""
                select id, user_id, refresh_family_id, created_at, last_rotated_at, expires_at,
                       revoked_at, revoke_reason, device_label
                from auth_sessions
                where id = ?
                for update
                """, SESSION_MAPPER, sessionId));
    }

    List<AuthModels.Session> findActiveSessionsForUpdate(UUID userId, Instant now) {
        return jdbc.query("""
                select id, user_id, refresh_family_id, created_at, last_rotated_at, expires_at,
                       revoked_at, revoke_reason, device_label
                from auth_sessions
                where user_id = ? and revoked_at is null and expires_at > ?
                order by created_at
                for update
                """, SESSION_MAPPER, userId, timestamp(now));
    }

    void rotateCredential(
            AuthModels.Credential previous,
            UUID replacementId,
            String replacementHash,
            Instant now,
            Instant expiresAt
    ) {
        int updated = jdbc.update("""
                with retired as (
                    update auth_refresh_credentials
                    set used_at = ?, replacement_id = ?
                    where id = ? and used_at is null and revoked_at is null
                    returning auth_session_id, family_id
                )
                insert into auth_refresh_credentials (
                    id, auth_session_id, family_id, token_hash, issued_at, expires_at,
                    used_at, revoked_at, replacement_id
                )
                select ?, auth_session_id, family_id, ?, ?, ?, null, null, null
                from retired
                """, timestamp(now), replacementId, previous.id(), replacementId, replacementHash,
                timestamp(now), timestamp(expiresAt));
        if (updated != 1) {
            throw new IllegalStateException("refresh credential rotation 경쟁을 감지했습니다.");
        }
        jdbc.update(
                "update auth_sessions set last_rotated_at = ? where id = ?",
                timestamp(now),
                previous.authSessionId()
        );
    }

    boolean revokeSession(
            AuthModels.Session session,
            String reason,
            Instant now,
            Instant denyUntil,
            String traceId
    ) {
        int updated = jdbc.update("""
                update auth_sessions
                set revoked_at = ?, revoke_reason = ?
                where id = ? and revoked_at is null
                """, timestamp(now), reason, session.id());
        if (updated == 0) {
            return false;
        }
        jdbc.update("""
                update auth_refresh_credentials
                set revoked_at = coalesce(revoked_at, ?)
                where family_id = ?
                """, timestamp(now), session.refreshFamilyId());
        insertOutbox(session, reason, now, denyUntil, traceId);
        return true;
    }

    List<WithdrawalOutboxEvent> findUnpublishedWithdrawalEvents(int limit) {
        return jdbc.query("""
                select id, payload ->> 'userId' as user_id
                from auth_outbox_events
                where published_at is null
                  and event_type = 'AUTH_SESSION_REVOKED'
                  and payload ->> 'reason' = 'ACCOUNT_WITHDRAWAL'
                order by created_at, id
                limit ?
                """, (rows, rowNumber) -> new WithdrawalOutboxEvent(
                rows.getObject("id", UUID.class),
                rows.getObject("user_id", UUID.class)
        ), limit);
    }

    void markOutboxPublished(UUID eventId, Instant now) {
        jdbc.update("""
                update auth_outbox_events
                set published_at = ?
                where id = ? and published_at is null
                """, timestamp(now), eventId);
    }

    void recordOutboxDeliveryFailure(UUID eventId, String errorCode) {
        jdbc.update("""
                update auth_outbox_events
                set attempt_count = attempt_count + 1,
                    last_error_code = ?
                where id = ? and published_at is null
                """, errorCode, eventId);
    }

    void insertAudit(
            UUID userId,
            UUID authSessionId,
            String eventType,
            String reasonCode,
            Instant occurredAt,
            String traceId,
            Map<String, ?> metadata
    ) {
        jdbc.update("""
                insert into session_audits (
                    id, user_id, auth_session_id, event_type, reason_code, occurred_at, trace_id, metadata
                ) values (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """, UUID.randomUUID(), userId, authSessionId, eventType, reasonCode,
                timestamp(occurredAt), traceId, json(metadata));
    }

    private void insertOutbox(
            AuthModels.Session session,
            String reason,
            Instant now,
            Instant denyUntil,
            String traceId
    ) {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> payload = Map.of(
                "eventId", eventId.toString(),
                "eventType", "AUTH_SESSION_REVOKED",
                "eventVersion", 1,
                "occurredAt", now.toString(),
                "userId", session.userId().toString(),
                "authSessionId", session.id().toString(),
                "reason", reason,
                "denyUntil", denyUntil.toString(),
                "traceId", traceId
        );
        jdbc.update("""
                insert into auth_outbox_events (
                    id, aggregate_type, aggregate_id, event_type, event_version, payload, created_at
                ) values (?, 'AUTH_SESSION', ?, 'AUTH_SESSION_REVOKED', 1, ?::jsonb, ?)
                """, eventId, session.id(), json(payload), timestamp(now));
    }

    static String canonicalEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static AuthModels.User mapUser(ResultSet rows, int rowNumber) throws SQLException {
        return new AuthModels.User(
                rows.getObject("id", UUID.class),
                rows.getString("email"),
                rows.getString("display_name"),
                rows.getString("picture_url"),
                rows.getString("status"),
                instant(rows, "created_at"),
                instant(rows, "updated_at"),
                nullableInstant(rows, "last_login_at")
        );
    }

    private static AuthModels.Identity mapIdentity(ResultSet rows, int rowNumber) throws SQLException {
        return new AuthModels.Identity(
                rows.getObject("id", UUID.class),
                rows.getObject("user_id", UUID.class),
                rows.getString("provider"),
                rows.getString("provider_user_id"),
                rows.getString("password_hash"),
                instant(rows, "created_at"),
                nullableInstant(rows, "last_used_at")
        );
    }

    private static AuthModels.Session mapSession(ResultSet rows, int rowNumber) throws SQLException {
        return new AuthModels.Session(
                rows.getObject("id", UUID.class),
                rows.getObject("user_id", UUID.class),
                rows.getObject("refresh_family_id", UUID.class),
                instant(rows, "created_at"),
                instant(rows, "last_rotated_at"),
                instant(rows, "expires_at"),
                nullableInstant(rows, "revoked_at"),
                rows.getString("revoke_reason"),
                rows.getString("device_label")
        );
    }

    private static AuthModels.RefreshState mapRefreshState(ResultSet rows, int rowNumber) throws SQLException {
        AuthModels.Credential credential = new AuthModels.Credential(
                rows.getObject("credential_id", UUID.class),
                rows.getObject("auth_session_id", UUID.class),
                rows.getObject("family_id", UUID.class),
                rows.getString("token_hash"),
                instant(rows, "credential_issued_at"),
                instant(rows, "credential_expires_at"),
                nullableInstant(rows, "used_at"),
                nullableInstant(rows, "credential_revoked_at"),
                rows.getObject("replacement_id", UUID.class)
        );
        AuthModels.Session session = new AuthModels.Session(
                rows.getObject("session_id", UUID.class),
                rows.getObject("user_id", UUID.class),
                rows.getObject("refresh_family_id", UUID.class),
                instant(rows, "session_created_at"),
                instant(rows, "last_rotated_at"),
                instant(rows, "session_expires_at"),
                nullableInstant(rows, "session_revoked_at"),
                rows.getString("revoke_reason"),
                rows.getString("device_label")
        );
        AuthModels.User user = new AuthModels.User(
                rows.getObject("user_id", UUID.class),
                rows.getString("email"),
                rows.getString("display_name"),
                rows.getString("picture_url"),
                rows.getString("status"),
                instant(rows, "user_created_at"),
                instant(rows, "user_updated_at"),
                nullableInstant(rows, "last_login_at")
        );
        return new AuthModels.RefreshState(credential, session, user);
    }

    private static AuthModels.PasswordResetState mapPasswordResetState(ResultSet rows, int rowNumber) throws SQLException {
        AuthModels.PasswordResetToken token = new AuthModels.PasswordResetToken(
                rows.getObject("token_id", UUID.class),
                rows.getObject("token_user_id", UUID.class),
                rows.getString("token_hash"),
                rows.getString("request_ip_prefix"),
                instant(rows, "token_created_at"),
                instant(rows, "token_expires_at"),
                nullableInstant(rows, "used_at")
        );
        AuthModels.User user = new AuthModels.User(
                rows.getObject("user_id", UUID.class),
                rows.getString("email"),
                rows.getString("display_name"),
                rows.getString("picture_url"),
                rows.getString("status"),
                instant(rows, "user_created_at"),
                instant(rows, "user_updated_at"),
                nullableInstant(rows, "last_login_at")
        );
        return new AuthModels.PasswordResetState(token, user);
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        return rows.getTimestamp(column).toInstant();
    }

    private static Instant nullableInstant(ResultSet rows, String column) throws SQLException {
        Timestamp value = rows.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static <T> Optional<T> first(List<T> values) {
        return values.stream().findFirst();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String json(Map<String, ?> values) {
        try {
            return OBJECT_MAPPER.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("감사 payload 생성에 실패했습니다.", exception);
        }
    }

    private void advisoryLock(String scope) {
        jdbc.query(
                "select pg_advisory_xact_lock(hashtextextended(?, 0))",
                rows -> {
                    // PostgreSQL advisory lock 함수는 void를 반환하므로 결과값을 읽지 않는다.
                },
                scope
        );
    }

    record WithdrawalOutboxEvent(UUID eventId, UUID userId) {
    }
}
