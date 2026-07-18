package com.meetingmind.bff.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class LegacyBackendAuthClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void callsOnlyTheFixedLoginPathAndKeepsRememberMeOutOfTheBackendRequest() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://backend.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LegacyBackendAuthClient client = new LegacyBackendAuthClient(builder.build(), objectMapper);
        server.expect(once(), requestTo("http://backend.example/api/v1/auth/login"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("User-Agent", "BrowserInjectedHeader"))
                .andExpect(content().json("""
                        {"email":"user@example.com","password":"password-123!"}
                        """))
                .andRespond(withSuccess(tokenResponse(), MediaType.APPLICATION_JSON));

        LegacyAuthTokenResponse response = client.login(
                new BrowserAuthRequests.Login("user@example.com", "password-123!", true),
                "Browser\r\nInjectedHeader");

        assertThat(response.accessToken()).isEqualTo("legacy-access-secret");
        assertThat(response.refreshToken()).isEqualTo("legacy-refresh-secret");
        assertThat(response.toString()).isEqualTo("LegacyAuthTokenResponse[REDACTED]");
        server.verify();
    }

    @Test
    void normalizesGoogleCredentialFailureWithoutKeepingTheBackendError() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://backend.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LegacyBackendAuthClient client = new LegacyBackendAuthClient(builder.build(), objectMapper);
        server.expect(requestTo("http://backend.example/api/v1/auth/google"))
                .andRespond(withUnauthorizedRequest().body("""
                        {"code":"INVALID_CREDENTIALS","message":"provider raw detail"}
                        """));

        assertThatThrownBy(() -> client.google(
                        new BrowserAuthRequests.Google("credential-secret", false), "JUnit"))
                .isInstanceOfSatisfying(BffAuthException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("GOOGLE_CREDENTIAL_INVALID");
                    assertThat(exception.getMessage()).doesNotContain("provider raw detail");
                    assertThat(exception).hasNoCause();
                });
        server.verify();
    }

    @Test
    void refreshesOnlyThroughTheFixedBackendPath() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://backend.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LegacyBackendAuthClient client = new LegacyBackendAuthClient(builder.build(), objectMapper);
        server.expect(once(), requestTo("http://backend.example/api/v1/auth/refresh"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"refreshToken":"legacy-refresh-secret"}
                        """))
                .andRespond(withSuccess(tokenResponse(), MediaType.APPLICATION_JSON));

        LegacyAuthTokenResponse response = client.refresh("legacy-refresh-secret", "JUnit");

        assertThat(response.accessToken()).isEqualTo("legacy-access-secret");
        server.verify();
    }

    @Test
    void revokesOnlyThroughTheFixedBackendPath() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://backend.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LegacyBackendAuthClient client = new LegacyBackendAuthClient(builder.build(), objectMapper);
        server.expect(once(), requestTo("http://backend.example/api/v1/auth/logout"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer legacy-access-secret"))
                .andExpect(content().json("""
                        {"refreshToken":"legacy-refresh-secret"}
                        """))
                .andRespond(withSuccess());

        client.revokeBestEffort("Bearer", "legacy-access-secret", "legacy-refresh-secret");

        server.verify();
    }

    @Test
    void revokeFailureAuditDoesNotLogTokenMaterial(CapturedOutput output) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://backend.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LegacyBackendAuthClient client = new LegacyBackendAuthClient(builder.build(), objectMapper);
        server.expect(requestTo("http://backend.example/api/v1/auth/logout"))
                .andRespond(withServerError().body("provider-secret-detail"));

        client.revokeBestEffort("Bearer", "legacy-access-secret", "legacy-refresh-secret");

        assertThat(output.getAll())
                .contains("event=compat_auth_revoke_failed outcome=local_session_invalidated")
                .doesNotContain("legacy-access-secret")
                .doesNotContain("legacy-refresh-secret")
                .doesNotContain("provider-secret-detail");
        server.verify();
    }

    private String tokenResponse() {
        return """
                {
                  "accessToken":"legacy-access-secret",
                  "refreshToken":"legacy-refresh-secret",
                  "tokenType":"Bearer",
                  "expiresIn":3600,
                  "refreshExpiresIn":1209600,
                  "user":{
                    "id":"user-id",
                    "email":"user@example.com",
                    "displayName":"User",
                    "pictureUrl":null,
                    "status":"ACTIVE"
                  }
                }
                """;
    }
}
