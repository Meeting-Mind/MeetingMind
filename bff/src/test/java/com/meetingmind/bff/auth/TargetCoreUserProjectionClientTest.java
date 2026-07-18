package com.meetingmind.bff.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TargetCoreUserProjectionClientTest {

    private static final UUID AUTH_USER_ID =
            UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID AUTH_SESSION_ID =
            UUID.fromString("e655a7be-39b1-44eb-9559-419ea96e5c62");
    private static final String BFF_PRINCIPAL =
            "spiffe://meetingmind.internal/ns/meetingmind/sa/meetingmind-bff";

    @Test
    void sendsTheCoreAudienceTokenAndDeterministicProjectionOnlyToTheFixedPath() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://core.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TargetCoreUserProjectionClient client = new TargetCoreUserProjectionClient(
                builder.build(), true, BFF_PRINCIPAL);
        server.expect(once(), requestTo("http://core.example/internal/v1/users/projection"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer core-secret"))
                .andExpect(header(TargetAuthServiceClient.TEST_PRINCIPAL_HEADER, BFF_PRINCIPAL))
                .andExpect(content().json("""
                        {
                          "authUserId":"11111111-1111-4111-8111-111111111111",
                          "resourceUserId":"user-11111111-1111-4111-8111-111111111111",
                          "email":"user@example.com",
                          "displayName":"User",
                          "pictureUrl":null,
                          "status":"ACTIVE"
                        }
                        """))
                .andRespond(withSuccess());

        client.project(targetTokens());

        server.verify();
    }

    @Test
    void normalizesCoreFailureAndNeverCreatesAProjectionForLegacySchema() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://core.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TargetCoreUserProjectionClient client =
                new TargetCoreUserProjectionClient(builder.build(), false, "");
        server.expect(requestTo("http://core.example/internal/v1/users/projection"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.project(targetTokens()))
                .isInstanceOfSatisfying(
                        BffAuthException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.code())
                                .isEqualTo("USER_PROJECTION_UNAVAILABLE"));
        server.verify();
        client.project(new AuthTokenResponse(
                AuthTokenResponse.LEGACY_SCHEMA_VERSION,
                UUID.randomUUID(),
                Map.of(
                        AuthTokenResponse.LEGACY_AUDIENCE,
                        new AuthTokenResponse.AccessToken("legacy-secret", 3_600)),
                "legacy-refresh",
                "Bearer",
                1_209_600,
                targetTokens().user()));
    }

    private AuthTokenResponse targetTokens() {
        return new AuthTokenResponse(
                AuthTokenResponse.TARGET_SCHEMA_VERSION,
                AUTH_SESSION_ID,
                Map.of(
                        "meetingmind-core",
                        new AuthTokenResponse.AccessToken("core-secret", 600),
                        "meetingmind-ai",
                        new AuthTokenResponse.AccessToken("ai-secret", 600),
                        "meetingmind-livekit",
                        new AuthTokenResponse.AccessToken("livekit-secret", 600)),
                "refresh-secret",
                "Bearer",
                1_209_600,
                new AuthTokenResponse.User(
                        AUTH_USER_ID,
                        "user-" + AUTH_USER_ID,
                        "user@example.com",
                        "User",
                        null,
                        "ACTIVE"));
    }
}
