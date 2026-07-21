package com.meetingmind.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.auth.migration.LegacyAuthDataMigration;
import com.meetingmind.auth.runtime.AuthIntegrationTestConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@EnabledIfEnvironmentVariable(named = "AUTH_DB_INTEGRATION", matches = "true")
class AuthPostgresIntegrationTest {

    private static final Pattern ROLE_NAME = Pattern.compile("[a-z][a-z0-9_]{0,62}");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String BFF_PRINCIPAL =
            "spiffe://meetingmind.internal/ns/meetingmind/sa/meetingmind-bff";

    @Test
    void appliesSchemaStartsWithDatabaseReadinessAndEnforcesRuntimePrivileges() throws Exception {
        String url = requiredEnvironment("AUTH_TEST_POSTGRES_URL");
        String migrationUser = requiredEnvironment("AUTH_TEST_MIGRATION_USER");
        String migrationPassword = requiredEnvironment("AUTH_TEST_MIGRATION_PASSWORD");
        String runtimeUser = requiredEnvironment("AUTH_TEST_RUNTIME_USER");
        String runtimePassword = requiredEnvironment("AUTH_TEST_RUNTIME_PASSWORD");
        assertThat(runtimeUser).isEqualTo("meetingmind_auth_app");

        provisionRuntimeRole(url, migrationUser, migrationPassword, runtimeUser, runtimePassword);

        var environment = new StandardEnvironment();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.profiles.active", "integration");
        properties.put("AUTH_DB_URL", url);
        properties.put("spring.datasource.username", runtimeUser);
        properties.put("AUTH_DB_RUNTIME_PASSWORD", runtimePassword);
        properties.put("AUTH_DB_MIGRATION_USER", migrationUser);
        properties.put("AUTH_DB_MIGRATION_PASSWORD", migrationPassword);
        properties.put("meetingmind.auth.refresh-hash-secret", "integration-refresh-hash-secret-at-least-32-chars");
        properties.put("meetingmind.auth.password-bcrypt-cost", "10");
        properties.put("meetingmind.auth.google.client-ids[0]", "meetingmind-test-client");
        properties.put("meetingmind.auth.workload.allow-test-header", "true");
        properties.put("server.port", "0");
        environment.getPropertySources().addFirst(new MapPropertySource("authIntegration", properties));

        RuntimeEvidence evidence;
        try (var context = new SpringApplicationBuilder(
                MeetingMindAuthApplication.class,
                AuthIntegrationTestConfiguration.class
        )
                .environment(environment)
                .run()) {
            int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
            assertHealth(port, "liveness");
            assertHealth(port, "readiness");
            evidence = assertRuntimeApiContracts(port);
        }

        assertMigratedSchemaAndPrivileges(url, migrationUser, migrationPassword, runtimeUser);
        assertLegacyAuthDataMigration(url, migrationUser, migrationPassword);
        assertRuntimeDmlAndDdlBoundary(url, runtimeUser, runtimePassword);
        assertRuntimePersistence(
                url,
                migrationUser,
                migrationPassword,
                evidence
        );
        assertMissingSignerRollsBack(
                url,
                migrationUser,
                migrationPassword,
                runtimeUser,
                runtimePassword
        );
    }

    private RuntimeEvidence assertRuntimeApiContracts(int port) throws Exception {
        String email = "runtime-" + UUID.randomUUID() + "@meetingmind.test";
        String password = "Password-123!";

        HttpResponse<String> noPrincipal = post(port, "/internal/v1/auth/login", """
                {"email":"%s","password":"%s"}
                """.formatted(email, password), null);
        assertError(noPrincipal, 401, "WORKLOAD_AUTH_REQUIRED");

        HttpResponse<String> wrongPrincipal = post(port, "/internal/v1/auth/login", """
                {"email":"%s","password":"%s"}
                """.formatted(email, password), "spiffe://meetingmind.internal/ns/other/sa/attacker");
        assertError(wrongPrincipal, 403, "WORKLOAD_FORBIDDEN");

        HttpResponse<String> signup = post(port, "/internal/v1/auth/signup", """
                {
                  "email":"%s",
                  "password":"%s",
                  "displayName":"Runtime Test",
                  "clientContext":{"deviceLabel":"Chrome on macOS"}
                }
                """.formatted(email, password), BFF_PRINCIPAL);
        assertThat(signup.statusCode()).isEqualTo(201);
        JsonNode signupBody = tokenResponse(signup);
        UUID userId = UUID.fromString(signupBody.path("user").path("id").asText());
        UUID reusedSessionId = UUID.fromString(signupBody.path("authSessionId").asText());
        String reusedRefresh = signupBody.path("refreshToken").asText();

        HttpResponse<String> duplicate = post(port, "/internal/v1/auth/signup", """
                {"email":"%s","password":"%s","displayName":"Duplicate"}
                """.formatted(email, password), BFF_PRINCIPAL);
        assertError(duplicate, 409, "EMAIL_ALREADY_REGISTERED");

        HttpResponse<String> invalidLogin = post(port, "/internal/v1/auth/login", """
                {"email":"%s","password":"Wrong-Password-123!"}
                """.formatted(email), BFF_PRINCIPAL);
        assertError(invalidLogin, 401, "INVALID_CREDENTIALS");

        JsonNode loginBody = tokenResponse(post(port, "/internal/v1/auth/login", """
                {
                  "email":"%s",
                  "password":"%s",
                  "clientContext":{"deviceLabel":"Second browser"}
                }
                """.formatted(email, password), BFF_PRINCIPAL));
        UUID currentLogoutSessionId = UUID.fromString(loginBody.path("authSessionId").asText());

        String googleCredential = "valid-google:" + email;
        JsonNode googleBody = tokenResponse(post(port, "/internal/v1/auth/google", """
                {"credential":"%s","clientContext":{"deviceLabel":"Google browser"}}
                """.formatted(googleCredential), BFF_PRINCIPAL));
        assertThat(googleBody.path("user").path("id").asText()).isEqualTo(userId.toString());

        JsonNode rotated = tokenResponse(post(port, "/internal/v1/auth/refresh", """
                {"authSessionId":"%s","refreshToken":"%s"}
                """.formatted(reusedSessionId, reusedRefresh), BFF_PRINCIPAL));
        String rotatedRefresh = rotated.path("refreshToken").asText();
        assertThat(rotatedRefresh).isNotEqualTo(reusedRefresh);

        HttpResponse<String> reuse = post(port, "/internal/v1/auth/refresh", """
                {"authSessionId":"%s","refreshToken":"%s"}
                """.formatted(reusedSessionId, reusedRefresh), BFF_PRINCIPAL);
        assertError(reuse, 409, "REFRESH_REUSE_DETECTED");

        HttpResponse<String> revokedLeaf = post(port, "/internal/v1/auth/refresh", """
                {"authSessionId":"%s","refreshToken":"%s"}
                """.formatted(reusedSessionId, rotatedRefresh), BFF_PRINCIPAL);
        assertError(revokedLeaf, 401, "AUTH_SESSION_REVOKED");

        String revokeJson = """
                {"authSessionId":"%s","reason":"CURRENT_LOGOUT"}
                """.formatted(currentLogoutSessionId);
        assertThat(post(port, "/internal/v1/auth/revoke", revokeJson, BFF_PRINCIPAL).statusCode())
                .isEqualTo(204);
        assertThat(post(port, "/internal/v1/auth/revoke", revokeJson, BFF_PRINCIPAL).statusCode())
                .isEqualTo(204);

        JsonNode allDeviceOne = tokenResponse(post(port, "/internal/v1/auth/login", """
                {"email":"%s","password":"%s"}
                """.formatted(email, password), BFF_PRINCIPAL));
        JsonNode allDeviceTwo = tokenResponse(post(port, "/internal/v1/auth/login", """
                {"email":"%s","password":"%s"}
                """.formatted(email, password), BFF_PRINCIPAL));
        UUID currentAuthSessionId = UUID.fromString(allDeviceOne.path("authSessionId").asText());
        UUID secondAllDeviceSessionId = UUID.fromString(allDeviceTwo.path("authSessionId").asText());
        String secondAllDeviceRefresh = allDeviceTwo.path("refreshToken").asText();

        HttpResponse<String> malformedReauthentication = post(
                port,
                "/internal/v1/auth/reauthenticate",
                """
                {
                  "currentAuthSessionId":"%s",
                  "userId":"%s",
                  "method":"PASSWORD",
                  "password":"%s",
                  "credential":"%s"
                }
                """.formatted(currentAuthSessionId, userId, password, googleCredential),
                BFF_PRINCIPAL
        );
        assertError(malformedReauthentication, 400, "INVALID_REQUEST");

        HttpResponse<String> reauthenticationMismatch = post(
                port,
                "/internal/v1/auth/reauthenticate",
                """
                {
                  "currentAuthSessionId":"%s",
                  "userId":"%s",
                  "method":"PASSWORD",
                  "password":"%s"
                }
                """.formatted(currentAuthSessionId, UUID.randomUUID(), password),
                BFF_PRINCIPAL
        );
        assertError(reauthenticationMismatch, 403, "AUTH_SESSION_SUBJECT_MISMATCH");

        HttpResponse<String> wrongPasswordReauthentication = post(
                port,
                "/internal/v1/auth/reauthenticate",
                """
                {
                  "currentAuthSessionId":"%s",
                  "userId":"%s",
                  "method":"PASSWORD",
                  "password":"Wrong-Password-123!"
                }
                """.formatted(currentAuthSessionId, userId),
                BFF_PRINCIPAL
        );
        assertError(wrongPasswordReauthentication, 401, "REAUTHENTICATION_FAILED");

        HttpResponse<String> wrongGoogleReauthentication = post(
                port,
                "/internal/v1/auth/reauthenticate",
                """
                {
                  "currentAuthSessionId":"%s",
                  "userId":"%s",
                  "method":"GOOGLE",
                  "credential":"valid-google:other-%s@meetingmind.test"
                }
                """.formatted(currentAuthSessionId, userId, UUID.randomUUID()),
                BFF_PRINCIPAL
        );
        assertError(wrongGoogleReauthentication, 401, "REAUTHENTICATION_FAILED");

        HttpResponse<String> passwordReauthentication = post(
                port,
                "/internal/v1/auth/reauthenticate",
                """
                {
                  "currentAuthSessionId":"%s",
                  "userId":"%s",
                  "method":"PASSWORD",
                  "password":"%s"
                }
                """.formatted(currentAuthSessionId, userId, password),
                BFF_PRINCIPAL
        );
        assertThat(passwordReauthentication.statusCode()).isEqualTo(200);
        JsonNode passwordReauthenticationBody = OBJECT_MAPPER.readTree(passwordReauthentication.body());
        assertThat(passwordReauthenticationBody.size()).isEqualTo(1);
        assertThat(passwordReauthenticationBody.path("authenticatedAt").asText()).isNotBlank();

        HttpResponse<String> googleReauthentication = post(
                port,
                "/internal/v1/auth/reauthenticate",
                """
                {
                  "currentAuthSessionId":"%s",
                  "userId":"%s",
                  "method":"GOOGLE",
                  "credential":"%s"
                }
                """.formatted(currentAuthSessionId, userId, googleCredential),
                BFF_PRINCIPAL
        );
        assertThat(googleReauthentication.statusCode()).isEqualTo(200);
        JsonNode googleReauthenticationBody = OBJECT_MAPPER.readTree(googleReauthentication.body());
        assertThat(googleReauthenticationBody.size()).isEqualTo(1);
        String authenticatedAt = googleReauthenticationBody.path("authenticatedAt").asText();
        assertThat(authenticatedAt).isNotBlank();
        assertThat(googleReauthentication.body())
                .doesNotContain("accessToken", "refreshToken", "authSessionId");

        HttpResponse<String> mismatch = post(port, "/internal/v1/auth/revoke-all", """
                {
                  "currentAuthSessionId":"%s",
                  "userId":"%s",
                  "reason":"ALL_DEVICE_LOGOUT",
                  "authenticatedAt":"%s"
                }
                """.formatted(currentAuthSessionId, UUID.randomUUID(), Instant.now()), BFF_PRINCIPAL);
        assertError(mismatch, 403, "AUTH_SESSION_SUBJECT_MISMATCH");

        HttpResponse<String> stale = post(port, "/internal/v1/auth/revoke-all", """
                {
                  "currentAuthSessionId":"%s",
                  "userId":"%s",
                  "reason":"ALL_DEVICE_LOGOUT",
                  "authenticatedAt":"%s"
                }
                """.formatted(currentAuthSessionId, userId, Instant.now().minusSeconds(601)), BFF_PRINCIPAL);
        assertError(stale, 401, "RECENT_AUTH_REQUIRED");

        String revokeAllJson = """
                {
                  "currentAuthSessionId":"%s",
                  "userId":"%s",
                  "reason":"ALL_DEVICE_LOGOUT",
                  "authenticatedAt":"%s"
                }
                """.formatted(currentAuthSessionId, userId, authenticatedAt);
        assertThat(post(port, "/internal/v1/auth/revoke-all", revokeAllJson, BFF_PRINCIPAL).statusCode())
                .isEqualTo(204);
        assertThat(post(port, "/internal/v1/auth/revoke-all", revokeAllJson, BFF_PRINCIPAL).statusCode())
                .isEqualTo(204);

        HttpResponse<String> allDeviceRefresh = post(port, "/internal/v1/auth/refresh", """
                {"authSessionId":"%s","refreshToken":"%s"}
                """.formatted(secondAllDeviceSessionId, secondAllDeviceRefresh), BFF_PRINCIPAL);
        assertError(allDeviceRefresh, 401, "AUTH_SESSION_REVOKED");

        return new RuntimeEvidence(
                email,
                googleCredential,
                reusedRefresh,
                rotatedRefresh,
                userId,
                reusedSessionId,
                currentLogoutSessionId
        );
    }

    private JsonNode tokenResponse(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode())
                .withFailMessage("unexpected token response status=%s body=%s", response.statusCode(), response.body())
                .isIn(200, 201);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        JsonNode body = OBJECT_MAPPER.readTree(response.body());
        assertThat(body.path("accessTokens")).hasSize(3);
        assertThat(body.path("refreshToken").asText()).matches("mmr_[A-Za-z0-9_-]{43}");
        assertThat(body.path("refreshExpiresIn").asLong()).isBetween(1L, 1_209_600L);
        assertThat(body.path("tokenType").asText()).isEqualTo("Bearer");
        for (JsonNode access : body.path("accessTokens")) {
            assertThat(access.path("expiresIn").asLong()).isEqualTo(600);
            assertThat(access.path("token").asText()).isNotBlank();
        }
        return body;
    }

    private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        JsonNode body = OBJECT_MAPPER.readTree(response.body());
        assertThat(body.path("code").asText()).isEqualTo(code);
        assertThat(body.path("traceId").asText()).isNotBlank();
        assertThat(response.body()).doesNotContain("SQLException", "org.springframework", "credential=");
    }

    private HttpResponse<String> post(int port, String path, String body, String principal) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + path)
                )
                .header("Content-Type", "application/json")
                .header("X-Request-Id", "integration-" + UUID.randomUUID())
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (principal != null) {
            request.header("X-MeetingMind-Test-Principal", principal);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void provisionRuntimeRole(
            String url,
            String migrationUser,
            String migrationPassword,
            String runtimeUser,
            String runtimePassword
    ) throws Exception {
        assertThat(runtimeUser).matches(ROLE_NAME);
        try (var connection = DriverManager.getConnection(url, migrationUser, migrationPassword)) {
            boolean exists;
            try (var query = connection.prepareStatement(
                    "select exists(select 1 from pg_roles where rolname = ?)"
            )) {
                query.setString(1, runtimeUser);
                try (var rows = query.executeQuery()) {
                    rows.next();
                    exists = rows.getBoolean(1);
                }
            }
            String template = exists
                    ? "alter role %I login password %L"
                    : "create role %I login password %L";
            String roleDdl;
            try (var format = connection.prepareStatement("select format(?, ?, ?)")) {
                format.setString(1, template);
                format.setString(2, runtimeUser);
                format.setString(3, runtimePassword);
                try (var rows = format.executeQuery()) {
                    rows.next();
                    roleDdl = rows.getString(1);
                }
            }
            try (var statement = connection.createStatement()) {
                statement.execute(roleDdl);
            }
        }
    }

    private void assertHealth(int port, String group) throws Exception {
        var response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(
                                "http://127.0.0.1:" + port + "/actuator/health/" + group
                        ))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    private void assertMigratedSchemaAndPrivileges(
            String url,
            String migrationUser,
            String migrationPassword,
            String runtimeUser
    ) throws Exception {
        try (var connection = DriverManager.getConnection(url, migrationUser, migrationPassword)) {
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery(
                         "select version from flyway_schema_history where success order by installed_rank"
                 )) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("1");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("2");
                assertThat(rows.next()).isFalse();
            }

            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("""
                         select table_name
                         from information_schema.tables
                         where table_schema = 'public'
                           and (
                               table_name like 'auth_%'
                               or table_name = 'session_audits'
                           )
                         order by table_name
                         """)) {
                List<String> expected = List.of(
                        "auth_identities",
                        "auth_outbox_events",
                        "auth_refresh_credentials",
                        "auth_sessions",
                        "auth_users",
                        "session_audits"
                );
                var actual = new java.util.ArrayList<String>();
                while (rows.next()) {
                    actual.add(rows.getString(1));
                }
                assertThat(actual).containsExactlyElementsOf(expected);
            }

            assertThat(hasPrivilege(
                    connection,
                    "select has_schema_privilege(?, 'public', 'CREATE')",
                    runtimeUser
            )).isFalse();
            for (String privilege : List.of("SELECT", "INSERT", "UPDATE")) {
                assertThat(hasPrivilege(
                        connection,
                        "select has_table_privilege(?, 'public.auth_users', ?)",
                        runtimeUser,
                        privilege
                )).isTrue();
            }
            assertThat(hasPrivilege(
                    connection,
                    "select has_table_privilege(?, 'public.auth_users', 'DELETE')",
                    runtimeUser
            )).isFalse();
            assertThat(hasPrivilege(
                    connection,
                    "select has_table_privilege(?, 'public.session_audits', 'UPDATE')",
                    runtimeUser
            )).isFalse();
            assertThat(hasPrivilege(
                    connection,
                    "select has_table_privilege(?, 'public.flyway_schema_history', 'SELECT')",
                    runtimeUser
            )).isFalse();
        }
    }

    private boolean hasPrivilege(Connection connection, String sql, String... values) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setString(index + 1, values[index]);
            }
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getBoolean(1);
            }
        }
    }

    private void assertRuntimeDmlAndDdlBoundary(
            String url,
            String runtimeUser,
            String runtimePassword
    ) throws Exception {
        UUID userId = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, runtimeUser, runtimePassword)) {
            connection.setAutoCommit(false);
            try (var insert = connection.prepareStatement("""
                    insert into auth_users (id, email, display_name)
                    values (?, ?, ?)
                    """)) {
                insert.setObject(1, userId);
                insert.setString(2, "foundation-" + userId + "@meetingmind.test");
                insert.setString(3, "Foundation Test");
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
            try (var update = connection.prepareStatement(
                    "update auth_users set display_name = ? where id = ?"
            )) {
                update.setString(1, "Foundation Updated");
                update.setObject(2, userId);
                assertThat(update.executeUpdate()).isEqualTo(1);
            }
            connection.rollback();
            connection.setAutoCommit(true);

            assertThatThrownBy(() -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("create table runtime_must_not_create(id integer)");
                }
            }).isInstanceOfSatisfying(SQLException.class, error ->
                    assertThat(error.getSQLState()).isEqualTo("42501")
            );

            assertThatThrownBy(() -> {
                try (var delete = connection.prepareStatement(
                        "delete from auth_users where id = ?"
                )) {
                    delete.setObject(1, userId);
                    delete.executeUpdate();
                }
            }).isInstanceOfSatisfying(SQLException.class, error ->
                    assertThat(error.getSQLState()).isEqualTo("42501")
            );
        }
    }

    private void assertRuntimePersistence(
            String url,
            String migrationUser,
            String migrationPassword,
            RuntimeEvidence evidence
    ) throws Exception {
        try (var connection = DriverManager.getConnection(url, migrationUser, migrationPassword)) {
            try (var query = connection.prepareStatement("""
                    select i.password_hash
                    from auth_identities i
                    join auth_users u on u.id = i.user_id
                    where u.email = ? and i.provider = 'LOCAL'
                    """)) {
                query.setString(1, evidence.email());
                try (var rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).startsWith("$2");
                }
            }

            try (var query = connection.prepareStatement("""
                    select token_hash, replacement_id, revoked_at
                    from auth_refresh_credentials
                    where auth_session_id = ?
                    order by issued_at
                    """)) {
                query.setObject(1, evidence.reusedSessionId());
                try (var rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("token_hash")).startsWith("hmac_sha256$");
                    assertThat(rows.getObject("replacement_id", UUID.class)).isNotNull();
                    assertThat(rows.getTimestamp("revoked_at")).isNotNull();
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("token_hash")).startsWith("hmac_sha256$");
                    assertThat(rows.getTimestamp("revoked_at")).isNotNull();
                    assertThat(rows.next()).isFalse();
                }
            }

            assertSessionReason(connection, evidence.reusedSessionId(), "REFRESH_REUSE");
            assertSessionReason(connection, evidence.currentLogoutSessionId(), "CURRENT_LOGOUT");

            try (var query = connection.prepareStatement("""
                    select
                      count(*) filter (where revoked_at is null) as active_count,
                      count(*) as session_count
                    from auth_sessions
                    where user_id = ?
                    """)) {
                query.setObject(1, evidence.userId());
                try (var rows = query.executeQuery()) {
                    rows.next();
                    assertThat(rows.getInt("active_count")).isZero();
                    int sessionCount = rows.getInt("session_count");
                    assertThat(sessionCount).isEqualTo(5);
                    assertThat(outboxCount(connection, evidence.userId())).isEqualTo(sessionCount);
                }
            }

            assertThat(textMatchCount(
                    connection,
                    """
                    select count(*)
                    from session_audits
                    where user_id = ? and event_type = 'REAUTHENTICATION_SUCCESS'
                    """,
                    evidence.userId()
            )).isEqualTo(2);
            assertThat(textMatchCount(
                    connection,
                    """
                    select count(*)
                    from session_audits
                    where user_id = ? and event_type = 'REAUTHENTICATION_FAILURE'
                    """,
                    evidence.userId()
            )).isEqualTo(2);

            assertThat(textMatchCount(
                    connection,
                    "select count(*) from auth_refresh_credentials where token_hash in (?, ?)",
                    evidence.reusedRefresh(),
                    evidence.rotatedRefresh()
            )).isZero();
            assertThat(textMatchCount(
                    connection,
                    "select count(*) from auth_identities where provider_user_id = ? or password_hash = ?",
                    evidence.googleCredential(),
                    evidence.googleCredential()
            )).isZero();
            assertThat(textMatchCount(
                    connection,
                    """
                    select count(*)
                    from session_audits
                    where metadata::text like ? or coalesce(reason_code, '') like ?
                    """,
                    "%" + evidence.reusedRefresh() + "%",
                    "%" + evidence.reusedRefresh() + "%"
            )).isZero();
            assertThat(textMatchCount(
                    connection,
                    """
                    select count(*)
                    from auth_outbox_events
                    where payload::text like ? or payload::text like ?
                    """,
                    "%" + evidence.email() + "%",
                    "%" + evidence.rotatedRefresh() + "%"
            )).isZero();
            assertThat(textMatchCount(
                    connection,
                    "select count(*) from session_audits where event_type = 'REFRESH_REUSE' and user_id = ?",
                    evidence.userId()
            )).isEqualTo(1);
        }
    }

    private void assertLegacyAuthDataMigration(
            String url,
            String migrationUser,
            String migrationPassword
    ) throws Exception {
        String sourceSchema = "legacy_auth_" + UUID.randomUUID().toString().replace("-", "");
        String sourceUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + sourceSchema;
        UUID userId = UUID.randomUUID();
        UUID localIdentityId = UUID.randomUUID();
        UUID googleIdentityId = UUID.randomUUID();
        String legacyUserId = "user-" + userId;
        String email = "legacy-" + userId + "@meetingmind.test";
        String passwordHash = new BCryptPasswordEncoder(10).encode("Legacy-Password-123!");
        Instant createdAt = Instant.parse("2026-07-18T01:00:00Z");
        Instant lastUsedAt = Instant.parse("2026-07-18T02:00:00Z");

        try (var connection = DriverManager.getConnection(url, migrationUser, migrationPassword);
             var statement = connection.createStatement()) {
            statement.execute("create schema " + sourceSchema);
            statement.execute("""
                    create table %s.users (
                        id varchar(64) primary key,
                        auth_user_id uuid,
                        email varchar(320) not null,
                        display_name varchar(100) not null,
                        picture_url text,
                        status varchar(32) not null,
                        created_at timestamptz not null,
                        last_login_at timestamptz
                    )
                    """.formatted(sourceSchema));
            statement.execute("""
                    create table %s.auth_identities (
                        id varchar(64) primary key,
                        user_id varchar(64) not null references %s.users(id),
                        provider varchar(16) not null,
                        provider_user_id varchar(320) not null,
                        password_hash varchar(255),
                        created_at timestamptz not null,
                        last_used_at timestamptz
                    )
                    """.formatted(sourceSchema, sourceSchema));
        }

        try {
            try (var connection = DriverManager.getConnection(url, migrationUser, migrationPassword)) {
                try (var insertUser = connection.prepareStatement("""
                        insert into %s.users (
                            id, auth_user_id, email, display_name, picture_url,
                            status, created_at, last_login_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?)
                        """.formatted(sourceSchema))) {
                    insertUser.setString(1, legacyUserId);
                    insertUser.setObject(2, userId);
                    insertUser.setString(3, email.toUpperCase(Locale.ROOT));
                    insertUser.setString(4, "Legacy User");
                    insertUser.setString(5, "https://example.test/legacy.png");
                    insertUser.setString(6, "active");
                    insertUser.setTimestamp(7, java.sql.Timestamp.from(createdAt));
                    insertUser.setTimestamp(8, java.sql.Timestamp.from(lastUsedAt));
                    insertUser.executeUpdate();
                }
                try (var insertIdentity = connection.prepareStatement("""
                        insert into %s.auth_identities (
                            id, user_id, provider, provider_user_id, password_hash,
                            created_at, last_used_at
                        ) values (?, ?, ?, ?, ?, ?, ?)
                        """.formatted(sourceSchema))) {
                    insertIdentity.setString(1, "identity-" + localIdentityId);
                    insertIdentity.setString(2, legacyUserId);
                    insertIdentity.setString(3, "local");
                    insertIdentity.setString(4, email.toUpperCase(Locale.ROOT));
                    insertIdentity.setString(5, passwordHash);
                    insertIdentity.setTimestamp(6, java.sql.Timestamp.from(createdAt));
                    insertIdentity.setTimestamp(7, java.sql.Timestamp.from(lastUsedAt));
                    insertIdentity.executeUpdate();

                    insertIdentity.setString(1, "identity-" + googleIdentityId);
                    insertIdentity.setString(2, legacyUserId);
                    insertIdentity.setString(3, "google");
                    insertIdentity.setString(4, "google-subject-" + userId);
                    insertIdentity.setNull(5, java.sql.Types.VARCHAR);
                    insertIdentity.setTimestamp(6, java.sql.Timestamp.from(createdAt));
                    insertIdentity.setTimestamp(7, java.sql.Timestamp.from(lastUsedAt));
                    insertIdentity.executeUpdate();
                }
            }

            var dryRun = LegacyAuthDataMigration.run(migrationConfig(
                    LegacyAuthDataMigration.Mode.DRY_RUN,
                    sourceUrl,
                    url,
                    migrationUser,
                    migrationPassword
            ));
            assertThat(dryRun.userCount()).isEqualTo(1);
            assertThat(dryRun.identityCount()).isEqualTo(2);
            assertThat(dryRun.mismatchCount()).isEqualTo(3);

            var applied = LegacyAuthDataMigration.run(migrationConfig(
                    LegacyAuthDataMigration.Mode.APPLY,
                    sourceUrl,
                    url,
                    migrationUser,
                    migrationPassword
            ));
            assertThat(applied.mismatchCount()).isZero();

            var verified = LegacyAuthDataMigration.run(migrationConfig(
                    LegacyAuthDataMigration.Mode.VERIFY,
                    sourceUrl,
                    url,
                    migrationUser,
                    migrationPassword
            ));
            assertThat(verified.mismatchCount()).isZero();

            var reapplied = LegacyAuthDataMigration.run(migrationConfig(
                    LegacyAuthDataMigration.Mode.APPLY,
                    sourceUrl,
                    url,
                    migrationUser,
                    migrationPassword
            ));
            assertThat(reapplied.userCount()).isEqualTo(1);
            assertThat(reapplied.identityCount()).isEqualTo(2);
            assertThat(reapplied.mismatchCount()).isZero();

            try (var connection = DriverManager.getConnection(url, migrationUser, migrationPassword);
                 var query = connection.prepareStatement("""
                         select
                           u.email,
                           u.display_name,
                           i.provider,
                           i.provider_user_id,
                           i.password_hash
                         from auth_users u
                         join auth_identities i on i.user_id = u.id
                         where u.id = ?
                         order by i.provider
                         """)) {
                query.setObject(1, userId);
                try (var rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("email")).isEqualTo(email);
                    assertThat(rows.getString("provider")).isEqualTo("GOOGLE");
                    assertThat(rows.getString("password_hash")).isNull();
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("provider")).isEqualTo("LOCAL");
                    assertThat(rows.getString("provider_user_id")).isEqualTo(email);
                    assertThat(rows.getString("password_hash")).isEqualTo(passwordHash);
                    assertThat(rows.next()).isFalse();
                }
            }

            UUID invalidUserId = UUID.randomUUID();
            try (var connection = DriverManager.getConnection(url, migrationUser, migrationPassword);
                 var insertUser = connection.prepareStatement("""
                         insert into %s.users (
                             id, auth_user_id, email, display_name, status, created_at
                         ) values (?, ?, ?, ?, 'active', ?)
                         """.formatted(sourceSchema));
                 var insertIdentity = connection.prepareStatement("""
                         insert into %s.auth_identities (
                             id, user_id, provider, provider_user_id, password_hash, created_at
                         ) values (?, ?, 'local', ?, ?, ?)
                         """.formatted(sourceSchema))) {
                insertUser.setString(1, "user-" + invalidUserId);
                insertUser.setObject(2, UUID.randomUUID());
                insertUser.setString(3, "invalid-" + invalidUserId + "@meetingmind.test");
                insertUser.setString(4, "Invalid Projection");
                insertUser.setTimestamp(5, java.sql.Timestamp.from(createdAt));
                insertUser.executeUpdate();

                insertIdentity.setString(1, "identity-" + UUID.randomUUID());
                insertIdentity.setString(2, "user-" + invalidUserId);
                insertIdentity.setString(3, "invalid-" + invalidUserId + "@meetingmind.test");
                insertIdentity.setString(4, passwordHash);
                insertIdentity.setTimestamp(5, java.sql.Timestamp.from(createdAt));
                insertIdentity.executeUpdate();
            }

            assertThatThrownBy(() -> LegacyAuthDataMigration.run(migrationConfig(
                    LegacyAuthDataMigration.Mode.VERIFY,
                    sourceUrl,
                    url,
                    migrationUser,
                    migrationPassword
            )))
                    .isInstanceOf(LegacyAuthDataMigration.MigrationException.class)
                    .hasMessage("USER_PROJECTION_MISMATCH");
        } finally {
            try (var connection = DriverManager.getConnection(url, migrationUser, migrationPassword);
                 var statement = connection.createStatement()) {
                statement.execute("drop schema " + sourceSchema + " cascade");
            }
        }
    }

    private LegacyAuthDataMigration.Config migrationConfig(
            LegacyAuthDataMigration.Mode mode,
            String sourceUrl,
            String targetUrl,
            String user,
            String password
    ) {
        return new LegacyAuthDataMigration.Config(
                mode,
                sourceUrl,
                user,
                password,
                targetUrl,
                user,
                password
        );
    }

    private void assertMissingSignerRollsBack(
            String url,
            String migrationUser,
            String migrationPassword,
            String runtimeUser,
            String runtimePassword
    ) throws Exception {
        String email = "missing-signer-" + UUID.randomUUID() + "@meetingmind.test";
        var environment = new StandardEnvironment();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.profiles.active", "integration");
        properties.put("AUTH_DB_URL", url);
        properties.put("spring.datasource.username", runtimeUser);
        properties.put("AUTH_DB_RUNTIME_PASSWORD", runtimePassword);
        properties.put("AUTH_DB_MIGRATION_USER", migrationUser);
        properties.put("AUTH_DB_MIGRATION_PASSWORD", migrationPassword);
        properties.put("meetingmind.auth.refresh-hash-secret", "integration-refresh-hash-secret-at-least-32-chars");
        properties.put("meetingmind.auth.password-bcrypt-cost", "10");
        properties.put("meetingmind.auth.workload.allow-test-header", "true");
        properties.put("server.port", "0");
        environment.getPropertySources().addFirst(new MapPropertySource("missingSigner", properties));

        try (var context = new SpringApplicationBuilder(MeetingMindAuthApplication.class)
                .environment(environment)
                .run()) {
            int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
            HttpResponse<String> response = post(port, "/internal/v1/auth/signup", """
                    {"email":"%s","password":"Password-123!","displayName":"No Signer"}
                    """.formatted(email), BFF_PRINCIPAL);
            assertError(response, 503, "TOKEN_ISSUER_UNAVAILABLE");
        }

        try (var connection = DriverManager.getConnection(url, migrationUser, migrationPassword);
             var query = connection.prepareStatement("select count(*) from auth_users where email = ?")) {
            query.setString(1, email);
            try (var rows = query.executeQuery()) {
                rows.next();
                assertThat(rows.getInt(1)).isZero();
            }
        }
    }

    private void assertSessionReason(Connection connection, UUID sessionId, String reason) throws Exception {
        try (var query = connection.prepareStatement("""
                select revoke_reason
                from auth_sessions
                where id = ?
                """)) {
            query.setObject(1, sessionId);
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo(reason);
            }
        }
    }

    private int outboxCount(Connection connection, UUID userId) throws Exception {
        try (var query = connection.prepareStatement("""
                select count(*)
                from auth_outbox_events o
                join auth_sessions s on s.id = o.aggregate_id
                where s.user_id = ?
                """)) {
            query.setObject(1, userId);
            try (var rows = query.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private int textMatchCount(Connection connection, String sql, Object... values) throws Exception {
        try (var query = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                query.setObject(index + 1, values[index]);
            }
            try (var rows = query.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        assertThat(value).as(name).isNotBlank();
        return value;
    }

    private record RuntimeEvidence(
            String email,
            String googleCredential,
            String reusedRefresh,
            String rotatedRefresh,
            UUID userId,
            UUID reusedSessionId,
            UUID currentLogoutSessionId
    ) {
    }
}
