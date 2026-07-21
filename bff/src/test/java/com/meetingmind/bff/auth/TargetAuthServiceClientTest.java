package com.meetingmind.bff.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TargetAuthServiceClientTest {

    private static final UUID AUTH_USER_ID =
            UUID.fromString("0a5b7c1e-5d75-4dc0-a10e-a330d0583930");
    private static final UUID AUTH_SESSION_ID =
            UUID.fromString("e655a7be-39b1-44eb-9559-419ea96e5c62");
    private static final String BFF_PRINCIPAL =
            "spiffe://meetingmind.internal/ns/meetingmind/sa/meetingmind-bff";

    @Test
    void mapsTheFixedTargetLoginResponseWithoutExposingAuthUuidAsResourceId() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://auth.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TargetAuthServiceClient client = new TargetAuthServiceClient(
                builder.build(),
                new ObjectMapper().findAndRegisterModules(),
                true,
                BFF_PRINCIPAL);
        server.expect(once(), requestTo("http://auth.example/internal/v1/auth/login"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(TargetAuthServiceClient.TEST_PRINCIPAL_HEADER, BFF_PRINCIPAL))
                .andExpect(content().json("""
                        {
                          "email":"user@example.com",
                          "password":"password-123!",
                          "clientContext":{"deviceLabel":"JUnit"}
                        }
                        """))
                .andRespond(withSuccess(tokenResponse(), MediaType.APPLICATION_JSON));

        AuthTokenResponse response = client.login(
                new BrowserAuthRequests.Login("user@example.com", "password-123!", false),
                "JUnit");

        assertThat(response.schemaVersion()).isEqualTo(2);
        assertThat(response.authSessionId()).isEqualTo(AUTH_SESSION_ID);
        assertThat(response.user().authUserId()).isEqualTo(AUTH_USER_ID);
        assertThat(response.user().resourceUserId()).isEqualTo("user-" + AUTH_USER_ID);
        assertThat(response.accessTokens().keySet())
                .containsExactlyInAnyOrder(
                        "meetingmind-core", "meetingmind-ai", "meetingmind-livekit");
        assertThat(response.toString())
                .contains("tokens=REDACTED")
                .doesNotContain("core-secret");
        server.verify();
    }

    @Test
    void requiresEveryFixedAudienceExactlyOnce() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://auth.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TargetAuthServiceClient client = new TargetAuthServiceClient(
                builder.build(),
                new ObjectMapper().findAndRegisterModules(),
                false,
                "");
        server.expect(requestTo("http://auth.example/internal/v1/auth/login"))
                .andRespond(withSuccess(
                        """
                        {
                          "accessTokens":[
                            {"audience":"meetingmind-core","token":"core-secret","expiresIn":600},
                            {"audience":"meetingmind-ai","token":"ai-secret","expiresIn":600}
                          ],
                          "refreshToken":"mmr_refresh-secret",
                          "tokenType":"Bearer",
                          "refreshExpiresIn":1209600,
                          "authSessionId":"e655a7be-39b1-44eb-9559-419ea96e5c62",
                          "user":{
                            "id":"0a5b7c1e-5d75-4dc0-a10e-a330d0583930",
                            "email":"user@example.com",
                            "displayName":"User",
                            "pictureUrl":null,
                            "status":"ACTIVE"
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.login(
                        new BrowserAuthRequests.Login(
                                "user@example.com", "password-123!", false),
                        "JUnit"))
                .isInstanceOfSatisfying(
                        BffAuthException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("AUTH_SERVICE_INVALID_RESPONSE"));
        server.verify();
    }

    @Test
    void normalizesRefreshReuseToSessionInvalid() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://auth.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TargetAuthServiceClient client = new TargetAuthServiceClient(
                builder.build(),
                new ObjectMapper().findAndRegisterModules(),
                false,
                "");
        server.expect(requestTo("http://auth.example/internal/v1/auth/refresh"))
                .andRespond(withUnauthorizedRequest().body("""
                        {"code":"REFRESH_REUSE_DETECTED","message":"sensitive detail"}
                        """));

        assertThatThrownBy(() -> client.refresh(AUTH_SESSION_ID, "mmr_token", "JUnit"))
                .isInstanceOfSatisfying(BffAuthException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("SESSION_INVALID");
                    assertThat(exception.getMessage()).doesNotContain("sensitive detail");
                });
        server.verify();
    }

    @Test
    void reauthenticatesAndRevokesAllOnlyThroughFixedInternalPaths() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://auth.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TargetAuthServiceClient client = new TargetAuthServiceClient(
                builder.build(),
                new ObjectMapper().findAndRegisterModules(),
                true,
                BFF_PRINCIPAL);
        Instant authenticatedAt = Instant.parse("2026-07-18T04:10:00Z");

        server.expect(once(), requestTo("http://auth.example/internal/v1/auth/reauthenticate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(TargetAuthServiceClient.TEST_PRINCIPAL_HEADER, BFF_PRINCIPAL))
                .andExpect(content().json("""
                        {
                          "currentAuthSessionId":"e655a7be-39b1-44eb-9559-419ea96e5c62",
                          "userId":"0a5b7c1e-5d75-4dc0-a10e-a330d0583930",
                          "method":"PASSWORD",
                          "password":"password-123!"
                        }
                        """))
                .andRespond(withSuccess(
                        "{\"authenticatedAt\":\"2026-07-18T04:10:00Z\"}",
                        MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("http://auth.example/internal/v1/auth/revoke-all"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(TargetAuthServiceClient.TEST_PRINCIPAL_HEADER, BFF_PRINCIPAL))
                .andExpect(content().json("""
                        {
                          "currentAuthSessionId":"e655a7be-39b1-44eb-9559-419ea96e5c62",
                          "userId":"0a5b7c1e-5d75-4dc0-a10e-a330d0583930",
                          "reason":"ALL_DEVICE_LOGOUT",
                          "authenticatedAt":"2026-07-18T04:10:00Z"
                        }
                        """))
                .andRespond(withSuccess());

        assertThat(client.reauthenticate(
                        AUTH_SESSION_ID,
                        AUTH_USER_ID,
                        new BrowserAuthRequests.Reauthenticate(
                                "PASSWORD",
                                "password-123!",
                                null)))
                .isEqualTo(authenticatedAt);
        client.revokeAll(AUTH_SESSION_ID, AUTH_USER_ID, authenticatedAt);
        server.verify();
    }

    @Test
    void mapsStaleRecentAuthenticationToBrowserReauthenticationRequired() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://auth.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TargetAuthServiceClient client = new TargetAuthServiceClient(
                builder.build(),
                new ObjectMapper().findAndRegisterModules(),
                false,
                "");
        server.expect(requestTo("http://auth.example/internal/v1/auth/revoke-all"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "code":"RECENT_AUTH_REQUIRED",
                                  "message":"internal detail"
                                }
                                """));

        assertThatThrownBy(() -> client.revokeAll(
                        AUTH_SESSION_ID,
                        AUTH_USER_ID,
                        Instant.parse("2026-07-18T03:00:00Z")))
                .isInstanceOfSatisfying(BffAuthException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.code()).isEqualTo("REAUTHENTICATION_REQUIRED");
                    assertThat(exception.getMessage()).doesNotContain("internal detail");
                });
        server.verify();
    }

    private String tokenResponse() {
        return """
                {
                  "accessTokens":[
                    {"audience":"meetingmind-core","token":"core-secret","expiresIn":600},
                    {"audience":"meetingmind-ai","token":"ai-secret","expiresIn":600},
                    {"audience":"meetingmind-livekit","token":"livekit-secret","expiresIn":600}
                  ],
                  "refreshToken":"mmr_refresh-secret",
                  "tokenType":"Bearer",
                  "refreshExpiresIn":1209600,
                  "authSessionId":"e655a7be-39b1-44eb-9559-419ea96e5c62",
                  "user":{
                    "id":"0a5b7c1e-5d75-4dc0-a10e-a330d0583930",
                    "email":"user@example.com",
                    "displayName":"User",
                    "pictureUrl":null,
                    "status":"ACTIVE"
                  }
                }
                """;
    }
}
