package com.meetingmind.auth.migration;

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class LegacyAuthDataMigration {

    private static final int BATCH_SIZE = 500;
    private static final Pattern BCRYPT_HASH =
            Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private LegacyAuthDataMigration() {
    }

    public static void main(String[] args) {
        try {
            Result result = run(Config.fromEnvironment(System.getenv()));
            System.out.printf(
                    "AUTH_DATA_MIGRATION status=SUCCESS mode=%s users=%d identities=%d mismatches=%d%n",
                    result.mode(),
                    result.userCount(),
                    result.identityCount(),
                    result.mismatchCount()
            );
        } catch (MigrationException exception) {
            System.err.printf(
                    "AUTH_DATA_MIGRATION status=FAILED code=%s%n",
                    exception.code()
            );
            System.exit(1);
        }
    }

    public static Result run(Config config) {
        Objects.requireNonNull(config, "config");
        try (Connection source = DriverManager.getConnection(
                config.sourceUrl(),
                config.sourceUser(),
                config.sourcePassword()
        ); Connection target = DriverManager.getConnection(
                config.targetUrl(),
                config.targetUser(),
                config.targetPassword()
        )) {
            configureSource(source);
            configureTarget(target);
            try {
                createStagingTables(target);
                int userCount = stageUsers(source, target);
                int identityCount = stageIdentities(source, target);
                assertNoOwnershipConflicts(target);

                if (config.mode() == Mode.APPLY) {
                    applyUsers(target);
                    applyIdentities(target);
                }

                int mismatchCount = countMismatches(target);
                if (config.mode() != Mode.DRY_RUN && mismatchCount != 0) {
                    throw new MigrationException("RECONCILIATION_MISMATCH");
                }

                if (config.mode() == Mode.APPLY) {
                    target.commit();
                } else {
                    target.rollback();
                }
                source.rollback();
                return new Result(config.mode(), userCount, identityCount, mismatchCount);
            } catch (MigrationException exception) {
                rollbackQuietly(target);
                rollbackQuietly(source);
                throw exception;
            } catch (SQLException exception) {
                rollbackQuietly(target);
                rollbackQuietly(source);
                throw new MigrationException("DATABASE_OPERATION_FAILED", exception);
            }
        } catch (SQLException exception) {
            throw new MigrationException("DATABASE_CONNECTION_FAILED", exception);
        }
    }

    private static void configureSource(Connection source) throws SQLException {
        source.setAutoCommit(false);
        source.setReadOnly(true);
        source.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
    }

    private static void configureTarget(Connection target) throws SQLException {
        target.setAutoCommit(false);
        target.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
    }

    private static void createStagingTables(Connection target) throws SQLException {
        try (Statement statement = target.createStatement()) {
            statement.execute("""
                    create temporary table legacy_auth_users_stage (
                        id uuid primary key,
                        legacy_id varchar(64) not null unique,
                        email varchar(320) not null unique,
                        display_name varchar(200) not null,
                        picture_url text,
                        status varchar(20) not null,
                        created_at timestamptz not null,
                        updated_at timestamptz not null,
                        last_login_at timestamptz
                    ) on commit drop
                    """);
            statement.execute("""
                    create temporary table legacy_auth_identities_stage (
                        id uuid primary key,
                        legacy_id varchar(64) not null unique,
                        user_id uuid not null references legacy_auth_users_stage(id),
                        provider varchar(20) not null,
                        provider_user_id varchar(320) not null,
                        password_hash varchar(255),
                        created_at timestamptz not null,
                        last_used_at timestamptz,
                        unique (provider, provider_user_id),
                        unique (user_id, provider)
                    ) on commit drop
                    """);
        }
    }

    private static int stageUsers(Connection source, Connection target) throws SQLException {
        String selectSql = """
                select
                    u.id,
                    u.auth_user_id,
                    u.email,
                    u.display_name,
                    u.picture_url,
                    u.status,
                    u.created_at,
                    u.last_login_at
                from users u
                where exists (
                    select 1
                    from auth_identities i
                    where i.user_id = u.id
                )
                order by u.id
                """;
        String insertSql = """
                insert into legacy_auth_users_stage (
                    id, legacy_id, email, display_name, picture_url, status,
                    created_at, updated_at, last_login_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        int count = 0;
        try (PreparedStatement select = source.prepareStatement(selectSql);
             PreparedStatement insert = target.prepareStatement(insertSql)) {
            select.setFetchSize(BATCH_SIZE);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    String legacyId = rows.getString("id");
                    UUID derivedId = prefixedUuid(legacyId, "user-", "INVALID_USER_ID");
                    UUID projectedId = rows.getObject("auth_user_id", UUID.class);
                    if (projectedId == null || !projectedId.equals(derivedId)) {
                        throw new MigrationException("USER_PROJECTION_MISMATCH");
                    }

                    String email = canonicalEmail(rows.getString("email"));
                    String displayName = requiredText(
                            rows.getString("display_name"),
                            200,
                            "INVALID_DISPLAY_NAME"
                    );
                    String status = canonicalStatus(rows.getString("status"));
                    Instant createdAt = instant(rows, "created_at");
                    Instant lastLoginAt = nullableInstant(rows, "last_login_at");
                    Instant updatedAt = lastLoginAt == null || createdAt.isAfter(lastLoginAt)
                            ? createdAt
                            : lastLoginAt;

                    insert.setObject(1, derivedId);
                    insert.setString(2, legacyId);
                    insert.setString(3, email);
                    insert.setString(4, displayName);
                    insert.setString(5, rows.getString("picture_url"));
                    insert.setString(6, status);
                    insert.setTimestamp(7, Timestamp.from(createdAt));
                    insert.setTimestamp(8, Timestamp.from(updatedAt));
                    setNullableTimestamp(insert, 9, lastLoginAt);
                    insert.addBatch();
                    count++;
                    executeBatchIfNeeded(insert, count);
                }
            }
            executeRemainingBatch(insert, count);
        } catch (SQLException exception) {
            throw mapStagingFailure(exception);
        }
        return count;
    }

    private static int stageIdentities(Connection source, Connection target) throws SQLException {
        String selectSql = """
                select
                    i.id,
                    i.user_id,
                    u.auth_user_id,
                    u.email,
                    i.provider,
                    i.provider_user_id,
                    i.password_hash,
                    i.created_at,
                    i.last_used_at
                from auth_identities i
                join users u on u.id = i.user_id
                order by i.id
                """;
        String insertSql = """
                insert into legacy_auth_identities_stage (
                    id, legacy_id, user_id, provider, provider_user_id,
                    password_hash, created_at, last_used_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        int count = 0;
        try (PreparedStatement select = source.prepareStatement(selectSql);
             PreparedStatement insert = target.prepareStatement(insertSql)) {
            select.setFetchSize(BATCH_SIZE);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    String legacyId = rows.getString("id");
                    UUID identityId = prefixedUuid(legacyId, "identity-", "INVALID_IDENTITY_ID");
                    UUID userId = rows.getObject("auth_user_id", UUID.class);
                    if (userId == null) {
                        throw new MigrationException("USER_PROJECTION_MISMATCH");
                    }

                    String provider = canonicalProvider(rows.getString("provider"));
                    String providerUserId = requiredText(
                            rows.getString("provider_user_id"),
                            320,
                            "INVALID_PROVIDER_USER_ID"
                    );
                    String passwordHash = rows.getString("password_hash");
                    if ("LOCAL".equals(provider)) {
                        providerUserId = canonicalEmail(providerUserId);
                        if (!providerUserId.equals(canonicalEmail(rows.getString("email")))) {
                            throw new MigrationException("LOCAL_IDENTITY_EMAIL_MISMATCH");
                        }
                        if (passwordHash == null || !BCRYPT_HASH.matcher(passwordHash).matches()) {
                            throw new MigrationException("INVALID_PASSWORD_HASH");
                        }
                    } else if (passwordHash != null) {
                        throw new MigrationException("GOOGLE_PASSWORD_HASH_PRESENT");
                    }

                    insert.setObject(1, identityId);
                    insert.setString(2, legacyId);
                    insert.setObject(3, userId);
                    insert.setString(4, provider);
                    insert.setString(5, providerUserId);
                    insert.setString(6, passwordHash);
                    insert.setTimestamp(7, Timestamp.from(instant(rows, "created_at")));
                    setNullableTimestamp(insert, 8, nullableInstant(rows, "last_used_at"));
                    insert.addBatch();
                    count++;
                    executeBatchIfNeeded(insert, count);
                }
            }
            executeRemainingBatch(insert, count);
        } catch (SQLException exception) {
            throw mapStagingFailure(exception);
        }
        return count;
    }

    private static void assertNoOwnershipConflicts(Connection target) throws SQLException {
        assertZero(target, """
                select count(*)
                from auth_users target
                join legacy_auth_users_stage source on target.id = source.id
                where target.email <> source.email
                """, "USER_ID_OWNERSHIP_CONFLICT");
        assertZero(target, """
                select count(*)
                from auth_users target
                join legacy_auth_users_stage source on target.email = source.email
                where target.id <> source.id
                """, "USER_EMAIL_OWNERSHIP_CONFLICT");
        assertZero(target, """
                select count(*)
                from auth_identities target
                join legacy_auth_identities_stage source on target.id = source.id
                where target.user_id <> source.user_id
                   or target.provider <> source.provider
                   or target.provider_user_id <> source.provider_user_id
                """, "IDENTITY_ID_OWNERSHIP_CONFLICT");
        assertZero(target, """
                select count(*)
                from auth_identities target
                join legacy_auth_identities_stage source
                  on target.provider = source.provider
                 and target.provider_user_id = source.provider_user_id
                where target.id <> source.id or target.user_id <> source.user_id
                """, "PROVIDER_SUBJECT_OWNERSHIP_CONFLICT");
        assertZero(target, """
                select count(*)
                from auth_identities target
                join legacy_auth_identities_stage source
                  on target.user_id = source.user_id
                 and target.provider = source.provider
                where target.id <> source.id
                   or target.provider_user_id <> source.provider_user_id
                """, "USER_PROVIDER_OWNERSHIP_CONFLICT");
    }

    private static void applyUsers(Connection target) throws SQLException {
        try (Statement statement = target.createStatement()) {
            statement.executeUpdate("""
                    insert into auth_users (
                        id, email, display_name, picture_url, status,
                        created_at, updated_at, last_login_at
                    )
                    select
                        id, email, display_name, picture_url, status,
                        created_at, updated_at, last_login_at
                    from legacy_auth_users_stage
                    on conflict (id) do update
                    set display_name = excluded.display_name,
                        picture_url = excluded.picture_url,
                        status = excluded.status,
                        updated_at = excluded.updated_at,
                        last_login_at = excluded.last_login_at
                    """);
        }
    }

    private static void applyIdentities(Connection target) throws SQLException {
        try (Statement statement = target.createStatement()) {
            statement.executeUpdate("""
                    insert into auth_identities (
                        id, user_id, provider, provider_user_id, password_hash,
                        created_at, last_used_at
                    )
                    select
                        id, user_id, provider, provider_user_id, password_hash,
                        created_at, last_used_at
                    from legacy_auth_identities_stage
                    on conflict (id) do update
                    set password_hash = excluded.password_hash,
                        last_used_at = excluded.last_used_at
                    """);
        }
    }

    private static int countMismatches(Connection target) throws SQLException {
        return queryCount(target, """
                select count(*)
                from legacy_auth_users_stage source
                left join auth_users target on target.id = source.id
                where target.id is null
                   or target.email is distinct from source.email
                   or target.display_name is distinct from source.display_name
                   or target.picture_url is distinct from source.picture_url
                   or target.status is distinct from source.status
                   or target.created_at is distinct from source.created_at
                   or target.updated_at is distinct from source.updated_at
                   or target.last_login_at is distinct from source.last_login_at
                """) + queryCount(target, """
                select count(*)
                from legacy_auth_identities_stage source
                left join auth_identities target on target.id = source.id
                where target.id is null
                   or target.user_id is distinct from source.user_id
                   or target.provider is distinct from source.provider
                   or target.provider_user_id is distinct from source.provider_user_id
                   or target.password_hash is distinct from source.password_hash
                   or target.created_at is distinct from source.created_at
                   or target.last_used_at is distinct from source.last_used_at
                """);
    }

    private static void assertZero(Connection connection, String sql, String code) throws SQLException {
        if (queryCount(connection, sql) != 0) {
            throw new MigrationException(code);
        }
    }

    private static int queryCount(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static UUID prefixedUuid(String value, String prefix, String code) {
        if (value == null || !value.startsWith(prefix)) {
            throw new MigrationException(code);
        }
        try {
            UUID uuid = UUID.fromString(value.substring(prefix.length()));
            if (!value.equals(prefix + uuid)) {
                throw new MigrationException(code);
            }
            return uuid;
        } catch (IllegalArgumentException exception) {
            throw new MigrationException(code);
        }
    }

    private static String canonicalEmail(String value) {
        String email = requiredText(value, 320, "INVALID_EMAIL").toLowerCase(Locale.ROOT);
        if (email.length() < 3 || email.indexOf('@') <= 0) {
            throw new MigrationException("INVALID_EMAIL");
        }
        return email;
    }

    private static String canonicalStatus(String value) {
        String status = requiredText(value, 20, "INVALID_USER_STATUS").toUpperCase(Locale.ROOT);
        if (!status.equals("ACTIVE") && !status.equals("DISABLED")) {
            throw new MigrationException("INVALID_USER_STATUS");
        }
        return status;
    }

    private static String canonicalProvider(String value) {
        String provider = requiredText(value, 20, "INVALID_PROVIDER").toUpperCase(Locale.ROOT);
        if (!provider.equals("LOCAL") && !provider.equals("GOOGLE")) {
            throw new MigrationException("INVALID_PROVIDER");
        }
        return provider;
    }

    private static String requiredText(String value, int maxLength, String code) {
        if (value == null || value.isBlank()) {
            throw new MigrationException(code);
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new MigrationException(code);
        }
        return trimmed;
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        Timestamp value = rows.getTimestamp(column);
        if (value == null) {
            throw new MigrationException("MISSING_TIMESTAMP");
        }
        return value.toInstant();
    }

    private static Instant nullableInstant(ResultSet rows, String column) throws SQLException {
        Timestamp value = rows.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static void setNullableTimestamp(
            PreparedStatement statement,
            int index,
            Instant value
    ) throws SQLException {
        statement.setTimestamp(index, value == null ? null : Timestamp.from(value));
    }

    private static void executeBatchIfNeeded(PreparedStatement statement, int count) throws SQLException {
        if (count % BATCH_SIZE == 0) {
            statement.executeBatch();
        }
    }

    private static void executeRemainingBatch(PreparedStatement statement, int count) throws SQLException {
        if (count % BATCH_SIZE != 0) {
            statement.executeBatch();
        }
    }

    private static MigrationException mapStagingFailure(SQLException exception) {
        if ("23505".equals(exception.getSQLState())) {
            return new MigrationException("DUPLICATE_SOURCE_OWNERSHIP", exception);
        }
        if ("23503".equals(exception.getSQLState())) {
            return new MigrationException("SOURCE_USER_IDENTITY_MISMATCH", exception);
        }
        return new MigrationException("SOURCE_STAGING_FAILED", exception);
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // 원래 실패 코드를 보존한다.
        }
    }

    public enum Mode {
        DRY_RUN,
        APPLY,
        VERIFY
    }

    public record Config(
            Mode mode,
            String sourceUrl,
            String sourceUser,
            String sourcePassword,
            String targetUrl,
            String targetUser,
            String targetPassword
    ) {
        public Config {
            Objects.requireNonNull(mode, "mode");
            sourceUrl = requiredConfiguration(sourceUrl, "AUTH_MIGRATION_SOURCE_URL");
            sourceUser = requiredConfiguration(sourceUser, "AUTH_MIGRATION_SOURCE_USER");
            sourcePassword = requiredConfiguration(sourcePassword, "AUTH_MIGRATION_SOURCE_PASSWORD");
            targetUrl = requiredConfiguration(targetUrl, "AUTH_MIGRATION_TARGET_URL");
            targetUser = requiredConfiguration(targetUser, "AUTH_MIGRATION_TARGET_USER");
            targetPassword = requiredConfiguration(targetPassword, "AUTH_MIGRATION_TARGET_PASSWORD");
        }

        public static Config fromEnvironment(Map<String, String> environment) {
            String rawMode = environment.getOrDefault("AUTH_DATA_MIGRATION_MODE", "DRY_RUN");
            Mode mode;
            try {
                mode = Mode.valueOf(rawMode.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new MigrationException("INVALID_MIGRATION_MODE");
            }
            return new Config(
                    mode,
                    environment.get("AUTH_MIGRATION_SOURCE_URL"),
                    environment.get("AUTH_MIGRATION_SOURCE_USER"),
                    environment.get("AUTH_MIGRATION_SOURCE_PASSWORD"),
                    environment.get("AUTH_MIGRATION_TARGET_URL"),
                    environment.get("AUTH_MIGRATION_TARGET_USER"),
                    environment.get("AUTH_MIGRATION_TARGET_PASSWORD")
            );
        }

        private static String requiredConfiguration(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new MigrationException("MISSING_CONFIGURATION_" + name);
            }
            return value;
        }
    }

    public record Result(Mode mode, int userCount, int identityCount, int mismatchCount) {
    }

    public static final class MigrationException extends RuntimeException {
        private final String code;

        public MigrationException(String code) {
            super(code);
            this.code = code;
        }

        public MigrationException(String code, Throwable cause) {
            super(code, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
