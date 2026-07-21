package com.meetingmind.bff.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.bff.tokenvault.TokenBundlePayload;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class TargetAuthServiceClientTest {

    private HttpServer authServer;
    private HttpServer coreServer;

    @BeforeEach
    void startServers() throws IOException {
        authServer = server();
        coreServer = server();
    }

    @AfterEach
    void stopServers() {
        authServer.stop(0);
        coreServer.stop(0);
    }

    @Test
    void acceptsAllAudienceTokensAndProjectsWithOnlyTheCoreToken() {
        AtomicReference<String> authPrincipal = new AtomicReference<>();
        AtomicReference<String> projectionAuthorization = new AtomicReference<>();
        AtomicReference<String> projectionBody = new AtomicReference<>();
        authServer.createContext("/internal/v1/auth/login", exchange -> {
            authPrincipal.set(exchange.getRequestHeaders().getFirst("X-MeetingMind-Test-Principal"));
            write(exchange, 200, """
                    {
                      "accessTokens":[
                        {"audience":"meetingmind-core","token":"core-token","expiresIn":600},
                        {"audience":"meetingmind-ai","token":"ai-token","expiresIn":600},
                        {"audience":"meetingmind-livekit","token":"livekit-token","expiresIn":600}
                      ],
                      "refreshToken":"mmr_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNO123456",
                      "tokenType":"Bearer",
                      "refreshExpiresIn":1209600,
                      "authSessionId":"e655a7be-39b1-44eb-9559-419ea96e5c62",
                      "user":{"id":"0a5b7c1e-5d75-4dc0-a10e-a330d0583930","email":"member@meetingmind.test","displayName":"Member","pictureUrl":null,"status":"ACTIVE"}
                    }
                    """);
        });
        coreServer.createContext("/internal/v1/core/auth-users/projection", exchange -> {
            projectionAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            projectionBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            write(exchange, 200, "{}");
        });

        TargetAuthServiceClient client = client();
        LegacyAuthTokenResponse response = client.login(
                new BrowserAuthRequests.Login("member@meetingmind.test", "Password-123!", false),
                "JUnit"
        );
        client.projectUser(response);

        assertThat(response.authSessionId()).hasToString("e655a7be-39b1-44eb-9559-419ea96e5c62");
        assertThat(response.accessTokens()).containsKeys("meetingmind-core", "meetingmind-ai", "meetingmind-livekit");
        assertThat(response.accessTokens().get("meetingmind-ai"))
                .extracting(TokenBundlePayload.AccessToken::token)
                .isEqualTo("ai-token");
        assertThat(authPrincipal.get()).isEqualTo("spiffe://meetingmind.internal/ns/meetingmind/sa/meetingmind-bff");
        assertThat(projectionAuthorization.get()).isEqualTo("Bearer core-token");
        assertThat(projectionBody.get()).contains("member@meetingmind.test", "Member");
    }

    @Test
    void forwardsAccountCommandsOnlyToTargetAuth() {
        List<String> paths = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        authServer.createContext("/internal/v1/auth/password-reset-requests", exchange -> capture(exchange, paths, bodies, 202, ""));
        authServer.createContext("/internal/v1/auth/password-resets", exchange -> capture(exchange, paths, bodies, 204, ""));
        authServer.createContext("/internal/v1/auth/password", exchange -> capture(exchange, paths, bodies, 204, ""));
        authServer.createContext("/internal/v1/auth/profile", exchange -> capture(exchange, paths, bodies, 200, """
                {"id":"0a5b7c1e-5d75-4dc0-a10e-a330d0583930","email":"member@meetingmind.test","displayName":"Updated","pictureUrl":null,"status":"ACTIVE"}
                """));

        TargetAuthServiceClient client = client();
        client.requestPasswordReset("member@meetingmind.test", "127.0.0.0/24");
        client.resetPassword("mmpr_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNO123456", "Password-456!");
        client.changePassword(
                java.util.UUID.fromString("e655a7be-39b1-44eb-9559-419ea96e5c62"),
                java.util.UUID.fromString("0a5b7c1e-5d75-4dc0-a10e-a330d0583930"),
                "Password-123!",
                "Password-456!");
        BffAuthUser profile = client.updateProfile(
                java.util.UUID.fromString("0a5b7c1e-5d75-4dc0-a10e-a330d0583930"), "Updated");

        assertThat(paths).containsExactly(
                "/internal/v1/auth/password-reset-requests",
                "/internal/v1/auth/password-resets",
                "/internal/v1/auth/password",
                "/internal/v1/auth/profile");
        assertThat(bodies.getFirst()).contains("127.0.0.0/24");
        assertThat(bodies.get(2)).contains("currentAuthSessionId", "userId");
        assertThat(profile.displayName()).isEqualTo("Updated");
    }

    @Test
    void forwardsProfileImageAsMultipartOnlyToTargetAuth() {
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        authServer.createContext("/internal/v1/auth/profile-image", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            assertThat(exchange.getRequestHeaders().getFirst("X-MeetingMind-Test-Principal"))
                    .isEqualTo("spiffe://meetingmind.internal/ns/meetingmind/sa/meetingmind-bff");
            write(exchange, 200, """
                    {"id":"0a5b7c1e-5d75-4dc0-a10e-a330d0583930","email":"member@meetingmind.test","displayName":"Member","pictureUrl":"profile-images/0a5b7c1e-5d75-4dc0-a10e-a330d0583930/example.png","status":"ACTIVE"}
                    """);
        });

        BffAuthUser profile = client().updateProfileImage(
                java.util.UUID.fromString("0a5b7c1e-5d75-4dc0-a10e-a330d0583930"),
                "image/png",
                "avatar.png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        assertThat(contentType.get()).startsWith("multipart/form-data;boundary=");
        assertThat(body.get()).contains("name=\"userId\"", "name=\"image\"", "filename=\"avatar.png\"");
        assertThat(profile.pictureUrl()).startsWith("profile-images/");
    }

    @Test
    void keepsWithdrawalReservationInCoreAndDisableInTargetAuth() {
        List<String> corePaths = new ArrayList<>();
        AtomicReference<String> coreAuthorization = new AtomicReference<>();
        List<String> authPaths = new ArrayList<>();
        List<String> authBodies = new ArrayList<>();
        coreServer.createContext("/internal/v1/core/account-withdrawal/reservation", exchange -> {
            corePaths.add(exchange.getRequestURI().getPath());
            coreAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            write(exchange, 204, "");
        });
        coreServer.createContext("/internal/v1/core/account-withdrawal/complete", exchange -> {
            corePaths.add(exchange.getRequestURI().getPath());
            write(exchange, 204, "");
        });
        coreServer.createContext("/internal/v1/core/account-withdrawal/cancel", exchange -> {
            corePaths.add(exchange.getRequestURI().getPath());
            write(exchange, 204, "");
        });
        authServer.createContext("/internal/v1/auth/withdrawal", exchange -> capture(exchange, authPaths, authBodies, 204, ""));

        TargetAuthServiceClient client = client();
        client.prepareWithdrawal("Bearer", "core-token");
        client.withdraw(
                java.util.UUID.fromString("e655a7be-39b1-44eb-9559-419ea96e5c62"),
                java.util.UUID.fromString("0a5b7c1e-5d75-4dc0-a10e-a330d0583930"),
                java.time.Instant.parse("2026-07-20T08:00:00Z"));
        client.completeWithdrawal("Bearer", "core-token");
        client.cancelWithdrawal("Bearer", "core-token");

        assertThat(corePaths).containsExactly(
                "/internal/v1/core/account-withdrawal/reservation",
                "/internal/v1/core/account-withdrawal/complete",
                "/internal/v1/core/account-withdrawal/cancel");
        assertThat(coreAuthorization.get()).isEqualTo("Bearer core-token");
        assertThat(authPaths).containsExactly("/internal/v1/auth/withdrawal");
        assertThat(authBodies.getFirst()).contains("currentAuthSessionId", "userId", "authenticatedAt");
    }

    @Test
    void projectsProfileWithOnlyTheCoreAudienceToken() {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        coreServer.createContext("/internal/v1/core/auth-users/projection", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            write(exchange, 200, "{}");
        });

        client().projectProfile(
                new BffAuthUser(
                        "0a5b7c1e-5d75-4dc0-a10e-a330d0583930",
                        "member@meetingmind.test",
                        "Updated",
                        null,
                        "ACTIVE"),
                "Bearer",
                "core-profile-token");

        assertThat(authorization.get()).isEqualTo("Bearer core-profile-token");
        assertThat(body.get()).contains("member@meetingmind.test", "Updated");
    }

    private TargetAuthServiceClient client() {
        return new TargetAuthServiceClient(
                RestClient.builder().baseUrl(origin(authServer)).build(),
                RestClient.builder().baseUrl(origin(coreServer)).build(),
                new ObjectMapper(),
                "spiffe://meetingmind.internal/ns/meetingmind/sa/meetingmind-bff"
        );
    }

    private static HttpServer server() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        return server;
    }

    private static String origin(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void write(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    private static void capture(
            HttpExchange exchange,
            List<String> paths,
            List<String> bodies,
            int status,
            String response) throws IOException {
        paths.add(exchange.getRequestURI().getPath());
        bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        assertThat(exchange.getRequestHeaders().getFirst("X-MeetingMind-Test-Principal"))
                .isEqualTo("spiffe://meetingmind.internal/ns/meetingmind/sa/meetingmind-bff");
        write(exchange, status, response);
    }
}
