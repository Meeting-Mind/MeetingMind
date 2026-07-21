package com.meetingmind.bff.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

public final class TargetCoreUserProjectionClient implements CoreUserProjectionClient {

    private final RestClient restClient;
    private final String testPrincipal;

    public TargetCoreUserProjectionClient(
            RestClient restClient,
            boolean allowTestPrincipalHeader,
            String testPrincipal) {
        if (allowTestPrincipalHeader && (testPrincipal == null || testPrincipal.isBlank())) {
            throw new IllegalStateException("Core projection test principal is required when enabled");
        }
        this.restClient = restClient;
        this.testPrincipal = allowTestPrincipalHeader ? testPrincipal : null;
    }

    @Override
    public void project(AuthTokenResponse tokens) {
        if (tokens.schemaVersion() == AuthTokenResponse.LEGACY_SCHEMA_VERSION) {
            return;
        }
        AuthTokenResponse.AccessToken core = tokens.accessTokens().get("meetingmind-core");
        if (core == null) {
            throw unavailable();
        }
        AuthTokenResponse.User user = tokens.user();
        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri("/internal/v1/users/projection")
                    .header(HttpHeaders.AUTHORIZATION, tokens.tokenType() + " " + core.token());
            if (testPrincipal != null) {
                request.header(TargetAuthServiceClient.TEST_PRINCIPAL_HEADER, testPrincipal);
            }
            request.body(new ProjectionRequest(
                            user.authUserId(),
                            user.resourceUserId(),
                            user.email(),
                            user.displayName(),
                            user.pictureUrl(),
                            user.status()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private BffAuthException unavailable() {
        return BffAuthException.of(
                HttpStatus.SERVICE_UNAVAILABLE,
                "USER_PROJECTION_UNAVAILABLE",
                "사용자 정보를 준비하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }

    private record ProjectionRequest(
            java.util.UUID authUserId,
            String resourceUserId,
            String email,
            String displayName,
            String pictureUrl,
            String status) {
    }
}
