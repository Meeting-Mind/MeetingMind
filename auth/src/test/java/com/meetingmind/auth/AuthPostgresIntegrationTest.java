package com.meetingmind.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.auth.runtime.AuthIntegrationTestConfiguration;
import com.meetingmind.auth.runtime.PasswordResetDeliveryRecorder;
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
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

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
            evidence = assertRuntimeApiContracts(
                    port,
                    context.getBean(PasswordResetDeliveryRecorder.class)
            );
        }

        assertMigratedSchemaAndPrivileges(url, migrationUser, migrationPassword, runtimeUser);
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

    private RuntimeEvidence assertRuntimeApiContracts(
            int port,
            PasswordResetDeliveryRecorder passwordResetDelivery
    ) throws Exception {
        String email = "runtime-" + UUID.randomUUID() + "@meetingmind.test";
        String password = "Password-123!";
        String resetPassword = "Password-456!";

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
                """.formatted(currentAuthSessionId, userId, Instant.now());
        assertThat(post(port, "/internal/v1/auth/revoke-all", revokeAllJson, BFF_PRINCIPAL).statusCode())
                .isEqualTo(204);
        assertThat(post(port, "/internal/v1/auth/revoke-all", revokeAllJson, BFF_PRINCIPAL).statusCode())
                .isEqualTo(204);

        HttpResponse<String> allDeviceRefresh = post(port, "/internal/v1/auth/refresh", """
                {"authSessionId":"%s","refreshToken":"%s"}
                """.formatted(secondAllDeviceSessionId, secondAllDeviceRefresh), BFF_PRINCIPAL);
        assertError(allDeviceRefresh, 401, "AUTH_SESSION_REVOKED");

        HttpResponse<String> resetRequest = post(port, "/internal/v1/auth/password-reset-requests", """
                {"email":"%s","requestIpPrefix":"203.0.113.0/24"}
                """.formatted(email), BFF_PRINCIPAL);
        assertThat(resetRequest.statusCode()).isEqualTo(202);
        assertThat(OBJECT_MAPPER.readTree(resetRequest.body()).path("accepted").asBoolean()).isTrue();
        String resetToken = passwordResetDelivery.takeToken();
        assertThat(resetToken).matches("mmpr_[A-Za-z0-9_-]{43}");

        assertThat(post(port, "/internal/v1/auth/password-resets", """
                {"token":"%s","newPassword":"%s"}
                """.formatted(resetToken, resetPassword), BFF_PRINCIPAL).statusCode()).isEqualTo(204);
        assertError(post(port, "/internal/v1/auth/password-resets", """
                {"token":"%s","newPassword":"Password-789!"}
                """.formatted(resetToken), BFF_PRINCIPAL), 400, "PASSWORD_RESET_TOKEN_INVALID");

        JsonNode resetLogin = tokenResponse(post(port, "/internal/v1/auth/login", """
                {"email":"%s","password":"%s"}
                """.formatted(email, resetPassword), BFF_PRINCIPAL));
        assertThat(resetLogin.path("user").path("id").asText()).isEqualTo(userId.toString());
        assertThat(post(port, "/internal/v1/auth/revoke", """
                {"authSessionId":"%s","reason":"CURRENT_LOGOUT"}
                """.formatted(resetLogin.path("authSessionId").asText()), BFF_PRINCIPAL).statusCode())
                .isEqualTo(204);

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
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("3");
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
                        "auth_password_history",
                        "auth_password_reset_tokens",
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
                    assertThat(sessionCount).isGreaterThanOrEqualTo(5);
                    assertThat(outboxCount(connection, evidence.userId())).isEqualTo(sessionCount);
                }
            }

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
