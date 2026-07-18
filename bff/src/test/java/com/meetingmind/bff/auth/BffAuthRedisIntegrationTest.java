package com.meetingmind.bff.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.bff.MeetingMindBffApplication;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.FindByIndexNameSessionRepository;

@EnabledIfEnvironmentVariable(named = "BFF_REDIS_INTEGRATION", matches = "true")
@ExtendWith(OutputCaptureExtension.class)
class BffAuthRedisIntegrationTest {

    private static final String ACCESS_TOKEN = "legacy-access-plain-secret";
    private static final String REFRESH_TOKEN = "legacy-refresh-plain-secret";
    private static final String ROTATED_ACCESS_TOKEN = "rotated-access-plain-secret";
    private static final String ROTATED_REFRESH_TOKEN = "rotated-refresh-plain-secret";
    private static final String LOCAL_KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

    @Test
    void convertsBackendTokensIntoRedisSessionAndEncryptedVaultWithoutBrowserExposure() throws Exception {
        AtomicReference<String> backendRequestBody = new AtomicReference<>();
        HttpServer backend = backendStub(backendRequestBody);
        String testId = UUID.randomUUID().toString();
        String sessionNamespace = "meetingmind:bff:t013:session:" + testId;
        String vaultNamespace = "meetingmind:bff:t013:vault:" + testId;

        backend.start();
        try (ConfigurableApplicationContext context =
                application(backend.getAddress().getPort(), sessionNamespace, vaultNamespace)) {
            int bffPort = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            HttpClient browser = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

            HttpResponse<String> csrfResponse = browser.send(
                    HttpRequest.newBuilder(uri(bffPort, "/api/v1/auth/csrf")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            String anonymousCookie = cookiePair(csrfResponse);
            String csrfToken = objectMapper.readTree(csrfResponse.body()).get("token").asText();

            HttpResponse<String> loginResponse = browser.send(
                    HttpRequest.newBuilder(uri(bffPort, "/api/v1/auth/login"))
                            .header("Content-Type", "application/json")
                            .header("Cookie", anonymousCookie)
                            .header("X-CSRF-TOKEN", csrfToken)
                            .POST(HttpRequest.BodyPublishers.ofString("""
                                    {
                                      "email":"user@example.com",
                                      "password":"password-123!",
                                      "rememberMe":true
                                    }
                                    """))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(loginResponse.statusCode()).isEqualTo(200);
            assertThat(loginResponse.body())
                    .doesNotContain(ACCESS_TOKEN)
                    .doesNotContain(REFRESH_TOKEN)
                    .doesNotContain("accessToken")
                    .doesNotContain("refreshToken");
            String rememberCookieHeader = loginResponse.headers().firstValue("set-cookie").orElseThrow();
            assertThat(rememberCookieHeader)
                    .contains("mm-session=")
                    .contains("Max-Age=1209600")
                    .contains("HttpOnly")
                    .contains("SameSite=Strict");
            String authenticatedCookie = cookiePair(loginResponse);

            HttpResponse<String> sessionResponse = browser.send(
                    HttpRequest.newBuilder(uri(bffPort, "/api/v1/auth/session"))
                            .header("Cookie", authenticatedCookie)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode sessionJson = objectMapper.readTree(sessionResponse.body());
            assertThat(sessionResponse.statusCode()).isEqualTo(200);
            assertThat(sessionResponse.headers().firstValue("cache-control"))
                    .contains("no-store, private");
            assertThat(sessionJson.get("authenticated").asBoolean()).isTrue();
            assertThat(sessionJson.get("session").get("rememberMe").asBoolean()).isTrue();
            assertThat(sessionResponse.body())
                    .doesNotContain(ACCESS_TOKEN)
                    .doesNotContain(REFRESH_TOKEN);

            assertThat(backendRequestBody.get())
                    .contains("user@example.com")
                    .doesNotContain("rememberMe");
            assertRedisBoundaries(context, vaultNamespace, authenticatedCookie);
            cleanupRedis(context, sessionNamespace, vaultNamespace);
        } finally {
            backend.stop(0);
        }
    }

    @Test
    void browserContractRefreshAndLogoutFailClosedWithoutTokenExposure(CapturedOutput output) throws Exception {
        BackendObservation observation = new BackendObservation();
        HttpServer backend = browserContractBackendStub(observation);
        String testId = UUID.randomUUID().toString();
        String sessionNamespace = "meetingmind:bff:t016:session:" + testId;
        String vaultNamespace = "meetingmind:bff:t016:vault:" + testId;

        backend.start();
        try (ConfigurableApplicationContext context =
                application(backend.getAddress().getPort(), sessionNamespace, vaultNamespace)) {
            int bffPort = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            HttpClient browser = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

            HttpResponse<String> csrfResponse = browser.send(
                    HttpRequest.newBuilder(uri(bffPort, "/api/v1/auth/csrf")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            String anonymousCookie = cookiePair(csrfResponse);
            String csrfToken = objectMapper.readTree(csrfResponse.body()).get("token").asText();

            HttpResponse<String> missingCsrfLogin = browser.send(
                    HttpRequest.newBuilder(uri(bffPort, "/api/v1/auth/login"))
                            .header("Content-Type", "application/json")
                            .header("Cookie", anonymousCookie)
                            .POST(HttpRequest.BodyPublishers.ofString(loginBody()))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(missingCsrfLogin.statusCode()).isIn(401, 403);
            assertThat(observation.loginBody).hasValue(null);

            HttpResponse<String> loginResponse = browser.send(
                    HttpRequest.newBuilder(uri(bffPort, "/api/v1/auth/login"))
                            .header("Content-Type", "application/json")
                            .header("Cookie", anonymousCookie)
                            .header("X-CSRF-TOKEN", csrfToken)
                            .POST(HttpRequest.BodyPublishers.ofString(loginBody()))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(loginResponse.statusCode()).isEqualTo(200);
            assertTokenless(loginResponse.body());
            String authenticatedCookie = cookiePair(loginResponse);

            HttpResponse<String> publicRefresh = browser.send(
                    HttpRequest.newBuilder(uri(bffPort, "/api/v1/auth/refresh"))
                            .header("Cookie", authenticatedCookie)
                            .header("X-CSRF-TOKEN", csrfToken)
                            .POST(HttpRequest.BodyPublishers.ofString("{}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(publicRefresh.statusCode()).isEqualTo(404);

            HttpResponse<String> spacesResponse = browser.send(
                    HttpRequest.newBuilder(uri(bffPort, "/api/v1/spaces"))
                            .header("Cookie", authenticatedCookie)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(spacesResponse.statusCode()).isEqualTo(200);
            assertThat(spacesResponse.body()).contains("space-id");
            assertTokenless(spacesResponse.body());
            assertThat(observation.refreshCalls).hasValue(1);
            assertThat(observation.spacesAuthorization.get()).isEqualTo("Bearer " + ROTATED_ACCESS_TOKEN);
            assertThat(observation.refreshBody.get()).contains(REFRESH_TOKEN);
            assertRedisBoundaries(context, vaultNamespace, authenticatedCookie);

            HttpResponse<String> missingCsrfLogout = browser.send(
                    HttpRequest.newBuilder(uri(bffPort, "/api/v1/auth/logout"))
                            .header("Cookie", authenticatedCookie)
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(missingCsrfLogout.statusCode()).isEqualTo(403);
            assertThat(observation.logoutCalls).hasValue(0);

            HttpResponse<String> logoutResponse = browser.send(
                    HttpRequest.newBuilder(uri(bffPort, "/api/v1/auth/logout"))
                            .header("Cookie", authenticatedCookie)
                            .header("X-CSRF-TOKEN", csrfToken)
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(logoutResponse.statusCode()).isEqualTo(204);
            assertThat(logoutResponse.body()).isEmpty();
            assertThat(logoutResponse.headers().allValues("set-cookie"))
                    .anySatisfy(cookie -> assertThat(cookie).contains("mm-session=").contains("Max-Age=0"));
            assertThat(observation.logoutCalls).hasValue(1);
            assertThat(observation.logoutAuthorization.get()).isEqualTo("Bearer " + ROTATED_ACCESS_TOKEN);
            assertThat(observation.logoutBody.get()).contains(ROTATED_REFRESH_TOKEN);
            assertRedisDeleted(context, vaultNamespace, authenticatedCookie);

            HttpResponse<String> staleSession = browser.send(
                    HttpRequest.newBuilder(uri(bffPort, "/api/v1/spaces"))
                            .header("Cookie", authenticatedCookie)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(staleSession.statusCode()).isEqualTo(401);

            HttpResponse<String> newCsrfResponse = browser.send(
                    HttpRequest.newBuilder(uri(bffPort, "/api/v1/auth/csrf")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            String newCookie = cookiePair(newCsrfResponse);
            String newCsrf = objectMapper.readTree(newCsrfResponse.body()).get("token").asText();
            HttpResponse<String> idempotentLogout = browser.send(
                    HttpRequest.newBuilder(uri(bffPort, "/api/v1/auth/logout"))
                            .header("Cookie", newCookie)
                            .header("X-CSRF-TOKEN", newCsrf)
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(idempotentLogout.statusCode()).isEqualTo(204);
            assertThat(observation.logoutCalls).hasValue(1);

            String logs = output.getAll();
            assertThat(logs)
                    .doesNotContain(ACCESS_TOKEN)
                    .doesNotContain(REFRESH_TOKEN)
                    .doesNotContain(ROTATED_ACCESS_TOKEN)
                    .doesNotContain(ROTATED_REFRESH_TOKEN);
            cleanupRedis(context, sessionNamespace, vaultNamespace);
        } finally {
            backend.stop(0);
        }
    }

    @Test
    void reauthenticatesThenDeletesEveryIndexedTargetSessionAndTokenBundle() throws Exception {
        TargetLogoutObservation observation = new TargetLogoutObservation();
        HttpServer target = targetAuthAndCoreStub(observation);
        String testId = UUID.randomUUID().toString();
        String sessionNamespace = "meetingmind:bff:t024:session:" + testId;
        String vaultNamespace = "meetingmind:bff:t024:vault:" + testId;

        target.start();
        try (ConfigurableApplicationContext context = targetApplication(
                target.getAddress().getPort(),
                sessionNamespace,
                vaultNamespace)) {
            int bffPort = context.getEnvironment()
                    .getRequiredProperty("local.server.port", Integer.class);
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            HttpClient browser = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();

            BrowserSession first = targetLogin(browser, objectMapper, bffPort);
            BrowserSession second = targetLogin(browser, objectMapper, bffPort);
            @SuppressWarnings("unchecked")
            FindByIndexNameSessionRepository<Session> repository =
                    (FindByIndexNameSessionRepository<Session>)
                            context.getBean(SessionRepository.class);
            Session firstSession = repository.findById(sessionId(first.cookie()));
            firstSession.setAttribute(
                    BffSessionAttributes.AUTHENTICATED_AT,
                    Instant.now().minusSeconds(601));
            repository.save(firstSession);

            HttpResponse<String> staleLogoutAll = browser.send(
                    HttpRequest.newBuilder(uri(bffPort, "/api/v1/auth/logout-all"))
                            .header("Cookie", first.cookie())
                            .header("X-CSRF-TOKEN", first.csrf())
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(staleLogoutAll.statusCode()).isEqualTo(403);
            assertThat(objectMapper.readTree(staleLogoutAll.body()).path("code").asText())
                    .isEqualTo("REAUTHENTICATION_REQUIRED");
            assertThat(repository.findByPrincipalName(
                    "0a5b7c1e-5d75-4dc0-a10e-a330d0583930"))
                    .hasSize(2);

            HttpResponse<String> reauthentication = browser.send(
                    HttpRequest.newBuilder(uri(bffPort, "/api/v1/auth/reauthenticate"))
                            .header("Content-Type", "application/json")
                            .header("Cookie", first.cookie())
                            .header("X-CSRF-TOKEN", first.csrf())
                            .POST(HttpRequest.BodyPublishers.ofString("""
                                    {
                                      "method":"PASSWORD",
                                      "password":"password-123!"
                                    }
                                    """))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(reauthentication.statusCode()).isEqualTo(204);
            assertThat(observation.reauthenticationBody.get())
                    .contains(AUTH_SESSION_ID_FOR_FIRST_DEVICE)
                    .contains("0a5b7c1e-5d75-4dc0-a10e-a330d0583930")
                    .contains("\"method\":\"PASSWORD\"")
                    .doesNotContain("authenticatedAt");

            HttpResponse<String> logoutAll = browser.send(
                    HttpRequest.newBuilder(uri(bffPort, "/api/v1/auth/logout-all"))
                            .header("Cookie", first.cookie())
                            .header("X-CSRF-TOKEN", first.csrf())
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(logoutAll.statusCode()).isEqualTo(204);
            assertThat(observation.successfulRevokeAllCalls).hasValue(1);
            assertThat(repository.findByPrincipalName(
                    "0a5b7c1e-5d75-4dc0-a10e-a330d0583930"))
                    .isEmpty();
            assertThat(context.getBean(StringRedisTemplate.class)
                    .keys(vaultNamespace + ":*"))
                    .isEmpty();

            assertThat(protectedRequest(browser, bffPort, first.cookie()).statusCode())
                    .isEqualTo(401);
            assertThat(protectedRequest(browser, bffPort, second.cookie()).statusCode())
                    .isEqualTo(401);
            cleanupRedis(context, sessionNamespace, vaultNamespace);
        } finally {
            target.stop(0);
        }
    }

    private static final String AUTH_SESSION_ID_FOR_FIRST_DEVICE =
            "e655a7be-39b1-44eb-9559-419ea96e5c62";

    private HttpServer targetAuthAndCoreStub(TargetLogoutObservation observation) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/auth/login", exchange -> {
            int login = observation.loginCalls.incrementAndGet();
            String authSessionId = login == 1
                    ? AUTH_SESSION_ID_FOR_FIRST_DEVICE
                    : "9cbbdd20-a648-4be4-ae6f-2d31777202bb";
            byte[] response = ("""
                    {
                      "accessTokens":[
                        {"audience":"meetingmind-core","token":"core-secret-%d","expiresIn":600},
                        {"audience":"meetingmind-ai","token":"ai-secret-%d","expiresIn":600},
                        {"audience":"meetingmind-livekit","token":"livekit-secret-%d","expiresIn":600}
                      ],
                      "refreshToken":"mmr_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                      "tokenType":"Bearer",
                      "refreshExpiresIn":1209600,
                      "authSessionId":"%s",
                      "user":{
                        "id":"0a5b7c1e-5d75-4dc0-a10e-a330d0583930",
                        "email":"user@example.com",
                        "displayName":"User",
                        "pictureUrl":null,
                        "status":"ACTIVE"
                      }
                    }
                    """).formatted(login, login, login, authSessionId)
                    .getBytes(StandardCharsets.UTF_8);
            json(exchange, 200, response);
        });
        server.createContext("/internal/v1/auth/reauthenticate", exchange -> {
            observation.reauthenticationBody.set(
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = ("{\"authenticatedAt\":\""
                    + Instant.now()
                    + "\"}").getBytes(StandardCharsets.UTF_8);
            json(exchange, 200, response);
        });
        server.createContext("/internal/v1/auth/revoke-all", exchange -> {
            String body = new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            observation.revokeAllBody.set(body);
            Instant authenticatedAt = Instant.parse(
                    new ObjectMapper().readTree(body).path("authenticatedAt").asText());
            if (authenticatedAt.isBefore(Instant.now().minusSeconds(600))) {
                json(
                        exchange,
                        401,
                        """
                        {
                          "code":"RECENT_AUTH_REQUIRED",
                          "message":"최근 인증이 필요합니다."
                        }
                        """.getBytes(StandardCharsets.UTF_8));
                return;
            }
            observation.successfulRevokeAllCalls.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.createContext("/internal/v1/users/projection", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.createContext("/api/v1/spaces", exchange -> json(
                exchange,
                200,
                "[]".getBytes(StandardCharsets.UTF_8)));
        return server;
    }

    private ConfigurableApplicationContext targetApplication(
            int targetPort,
            String sessionNamespace,
            String vaultNamespace) {
        String principal =
                "spiffe://meetingmind.internal/ns/meetingmind/sa/meetingmind-bff";
        return new SpringApplicationBuilder(MeetingMindBffApplication.class)
                .profiles("redis-integration")
                .run(
                        "--server.port=0",
                        "--spring.data.redis.host="
                                + environment("BFF_REDIS_HOST", "127.0.0.1"),
                        "--spring.data.redis.port="
                                + environment("BFF_REDIS_PORT", "6380"),
                        "--spring.session.store-type=redis",
                        "--spring.session.redis.namespace=" + sessionNamespace,
                        "--spring.session.redis.repository-type=indexed",
                        "--meetingmind.bff.session-cookie.name=mm-session",
                        "--meetingmind.bff.session-cookie.secure=false",
                        "--meetingmind.bff.token-vault.key-provider=local",
                        "--meetingmind.bff.token-vault.namespace=" + vaultNamespace,
                        "--meetingmind.bff.token-vault.local-key-id=t024-integration-test",
                        "--meetingmind.bff.token-vault.local-master-key-base64=" + LOCAL_KEY,
                        "--meetingmind.bff.auth-provider=auth-service",
                        "--meetingmind.bff.auth-issuer=https://auth.meetingmind.internal",
                        "--meetingmind.bff.target-auth.base-url=http://127.0.0.1:" + targetPort,
                        "--meetingmind.bff.target-auth.allow-test-principal-header=true",
                        "--meetingmind.bff.target-auth.test-principal=" + principal,
                        "--meetingmind.bff.core-projection.base-url=http://127.0.0.1:" + targetPort,
                        "--meetingmind.bff.core-projection.allow-test-principal-header=true",
                        "--meetingmind.bff.core-projection.test-principal=" + principal,
                        "--meetingmind.bff.downstream.core.base-url=http://127.0.0.1:" + targetPort,
                        "--management.health.redis.enabled=false");
    }

    private BrowserSession targetLogin(
            HttpClient browser,
            ObjectMapper objectMapper,
            int bffPort) throws Exception {
        HttpResponse<String> csrfResponse = browser.send(
                HttpRequest.newBuilder(uri(bffPort, "/api/v1/auth/csrf"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        String anonymousCookie = cookiePair(csrfResponse);
        String csrf = objectMapper.readTree(csrfResponse.body()).path("token").asText();
        HttpResponse<String> login = browser.send(
                HttpRequest.newBuilder(uri(bffPort, "/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .header("Cookie", anonymousCookie)
                        .header("X-CSRF-TOKEN", csrf)
                        .POST(HttpRequest.BodyPublishers.ofString(loginBody()))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(200);
        assertTokenless(login.body());
        return new BrowserSession(cookiePair(login), csrf);
    }

    private HttpResponse<String> protectedRequest(
            HttpClient browser,
            int bffPort,
            String cookie) throws Exception {
        return browser.send(
                HttpRequest.newBuilder(uri(bffPort, "/api/v1/spaces"))
                        .header("Cookie", cookie)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String sessionId(String cookie) {
        String encoded = cookie.substring(cookie.indexOf('=') + 1);
        return new String(
                Base64.getDecoder().decode(encoded),
                StandardCharsets.UTF_8);
    }

    private void json(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private HttpServer backendStub(AtomicReference<String> requestBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/auth/login", exchange -> respondWithTokens(exchange, requestBody));
        return server;
    }

    private HttpServer browserContractBackendStub(BackendObservation observation) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/auth/login", exchange -> respondWithTokens(
                exchange, observation.loginBody, ACCESS_TOKEN, REFRESH_TOKEN, 10));
        server.createContext("/api/v1/auth/refresh", exchange -> {
            observation.refreshCalls.incrementAndGet();
            respondWithTokens(
                    exchange, observation.refreshBody, ROTATED_ACCESS_TOKEN, ROTATED_REFRESH_TOKEN, 3_600);
        });
        server.createContext("/api/v1/auth/logout", exchange -> {
            observation.logoutCalls.incrementAndGet();
            observation.logoutAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            observation.logoutBody.set(
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.createContext("/api/v1/spaces", exchange -> {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            observation.spacesAuthorization.set(authorization);
            if (!("Bearer " + ROTATED_ACCESS_TOKEN).equals(authorization)) {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
            byte[] body = "[{\"id\":\"space-id\"}]".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        return server;
    }

    private void respondWithTokens(HttpExchange exchange, AtomicReference<String> requestBody) throws IOException {
        respondWithTokens(exchange, requestBody, ACCESS_TOKEN, REFRESH_TOKEN, 3_600);
    }

    private void respondWithTokens(
            HttpExchange exchange,
            AtomicReference<String> requestBody,
            String accessToken,
            String refreshToken,
            long expiresIn) throws IOException {
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] response = ("""
                {
                  "accessToken":"%s",
                  "refreshToken":"%s",
                  "tokenType":"Bearer",
                  "expiresIn":%d,
                  "refreshExpiresIn":1209600,
                  "user":{
                    "id":"user-0a5b7c1e-5d75-4dc0-a10e-a330d0583930",
                    "email":"user@example.com",
                    "displayName":"User",
                    "pictureUrl":null,
                    "status":"ACTIVE"
                  }
                }
                """).formatted(accessToken, refreshToken, expiresIn).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private ConfigurableApplicationContext application(
            int backendPort, String sessionNamespace, String vaultNamespace) {
        return new SpringApplicationBuilder(MeetingMindBffApplication.class)
                .profiles("redis-integration")
                .run(
                        "--server.port=0",
                        "--spring.data.redis.host=" + environment("BFF_REDIS_HOST", "127.0.0.1"),
                        "--spring.data.redis.port=" + environment("BFF_REDIS_PORT", "6380"),
                        "--spring.session.store-type=redis",
                        "--spring.session.redis.namespace=" + sessionNamespace,
                        "--spring.session.redis.repository-type=indexed",
                        "--meetingmind.bff.session-cookie.name=mm-session",
                        "--meetingmind.bff.session-cookie.secure=false",
                        "--meetingmind.bff.token-vault.key-provider=local",
                        "--meetingmind.bff.token-vault.namespace=" + vaultNamespace,
                        "--meetingmind.bff.token-vault.local-key-id=t013-integration-test",
                        "--meetingmind.bff.token-vault.local-master-key-base64=" + LOCAL_KEY,
                        "--meetingmind.bff.auth-provider=legacy",
                        "--meetingmind.bff.auth-issuer=meetingmind-core-legacy",
                        "--meetingmind.bff.compat-auth.base-url=http://127.0.0.1:" + backendPort,
                        "--meetingmind.bff.core-projection.base-url=http://127.0.0.1:" + backendPort,
                        "--meetingmind.bff.downstream.core.base-url=http://127.0.0.1:" + backendPort,
                        "--management.health.redis.enabled=false");
    }

    @SuppressWarnings("unchecked")
    private void assertRedisBoundaries(
            ConfigurableApplicationContext context, String vaultNamespace, String authenticatedCookie) {
        StringRedisTemplate redis = context.getBean(StringRedisTemplate.class);
        Set<String> vaultKeys = redis.keys(vaultNamespace + ":*");
        assertThat(vaultKeys).hasSize(1);
        String persistedVault = redis.opsForValue().get(vaultKeys.iterator().next());
        assertThat(persistedVault)
                .doesNotContain(ACCESS_TOKEN)
                .doesNotContain(REFRESH_TOKEN);

        String encodedSessionId = authenticatedCookie.substring(authenticatedCookie.indexOf('=') + 1);
        String sessionId = new String(Base64.getDecoder().decode(encodedSessionId), StandardCharsets.UTF_8);
        SessionRepository<Session> repository =
                (SessionRepository<Session>) context.getBean(SessionRepository.class);
        Session session = repository.findById(sessionId);
        assertThat(session).isNotNull();
        assertThat(session.getAttributeNames())
                .contains(
                        BffSessionAttributes.RESOURCE_USER_ID,
                        BffSessionAttributes.AUTH_USER_ID,
                        BffSessionAttributes.AUTH_SESSION_ID,
                        BffSessionAttributes.TOKEN_BUNDLE_ID,
                        BffSessionAttributes.ABSOLUTE_EXPIRES_AT)
                .doesNotContain("accessToken", "refreshToken");
        assertThat(repository).isInstanceOf(FindByIndexNameSessionRepository.class);
        @SuppressWarnings("rawtypes")
        FindByIndexNameSessionRepository indexed =
                (FindByIndexNameSessionRepository) repository;
        assertThat(indexed.findByPrincipalName(
                        "0a5b7c1e-5d75-4dc0-a10e-a330d0583930"))
                .containsKey(sessionId);
    }

    private void cleanupRedis(
            ConfigurableApplicationContext context, String sessionNamespace, String vaultNamespace) {
        StringRedisTemplate redis = context.getBean(StringRedisTemplate.class);
        Set<String> keys = new java.util.HashSet<>();
        Set<String> sessionKeys = redis.keys(sessionNamespace + "*");
        Set<String> vaultKeys = redis.keys(vaultNamespace + "*");
        if (sessionKeys != null) {
            keys.addAll(sessionKeys);
        }
        if (vaultKeys != null) {
            keys.addAll(vaultKeys);
        }
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private void assertRedisDeleted(
            ConfigurableApplicationContext context,
            String vaultNamespace,
            String authenticatedCookie) {
        StringRedisTemplate redis = context.getBean(StringRedisTemplate.class);
        assertThat(redis.keys(vaultNamespace + "*")).isEmpty();
        String encodedSessionId = authenticatedCookie.substring(authenticatedCookie.indexOf('=') + 1);
        String sessionId = new String(
                Base64.getDecoder().decode(encodedSessionId), StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        FindByIndexNameSessionRepository<Session> repository =
                (FindByIndexNameSessionRepository<Session>)
                        context.getBean(SessionRepository.class);
        assertThat(repository.findById(sessionId)).isNull();
        assertThat(repository.findByPrincipalName(
                        "0a5b7c1e-5d75-4dc0-a10e-a330d0583930"))
                .isEmpty();
    }

    private void assertTokenless(String body) {
        assertThat(body)
                .doesNotContain(ACCESS_TOKEN)
                .doesNotContain(REFRESH_TOKEN)
                .doesNotContain(ROTATED_ACCESS_TOKEN)
                .doesNotContain(ROTATED_REFRESH_TOKEN)
                .doesNotContain("accessToken")
                .doesNotContain("refreshToken");
    }

    private String loginBody() {
        return """
                {
                  "email":"user@example.com",
                  "password":"password-123!",
                  "rememberMe":false
                }
                """;
    }

    private URI uri(int port, String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private String cookiePair(HttpResponse<?> response) {
        return response.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
    }

    private String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static final class BackendObservation {

        private final AtomicInteger refreshCalls = new AtomicInteger();
        private final AtomicInteger logoutCalls = new AtomicInteger();
        private final AtomicReference<String> loginBody = new AtomicReference<>();
        private final AtomicReference<String> refreshBody = new AtomicReference<>();
        private final AtomicReference<String> spacesAuthorization = new AtomicReference<>();
        private final AtomicReference<String> logoutAuthorization = new AtomicReference<>();
        private final AtomicReference<String> logoutBody = new AtomicReference<>();
    }

    private record BrowserSession(String cookie, String csrf) {
    }

    private static final class TargetLogoutObservation {

        private final AtomicInteger loginCalls = new AtomicInteger();
        private final AtomicInteger successfulRevokeAllCalls = new AtomicInteger();
        private final AtomicReference<String> reauthenticationBody = new AtomicReference<>();
        private final AtomicReference<String> revokeAllBody = new AtomicReference<>();
    }
}
