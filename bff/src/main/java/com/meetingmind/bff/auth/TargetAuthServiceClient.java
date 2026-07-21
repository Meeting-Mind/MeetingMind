package com.meetingmind.bff.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public final class TargetAuthServiceClient implements AuthClient {

    static final String TEST_PRINCIPAL_HEADER = "X-MeetingMind-Test-Principal";
    static final Set<String> REQUIRED_AUDIENCES =
            Set.of("meetingmind-core", "meetingmind-ai", "meetingmind-livekit");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String testPrincipal;

    public TargetAuthServiceClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            boolean allowTestPrincipalHeader,
            String testPrincipal) {
        if (allowTestPrincipalHeader && (testPrincipal == null || testPrincipal.isBlank())) {
            throw new IllegalStateException("target Auth test principal is required when the test header is enabled");
        }
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.testPrincipal = allowTestPrincipalHeader ? testPrincipal : null;
    }

    @Override
    public AuthTokenResponse signup(BrowserAuthRequests.Signup request, String userAgent) {
        return exchange(
                "/internal/v1/auth/signup",
                new SignupRequest(
                        request.email(),
                        request.password(),
                        request.displayName(),
                        clientContext(userAgent)),
                false);
    }

    @Override
    public AuthTokenResponse login(BrowserAuthRequests.Login request, String userAgent) {
        return exchange(
                "/internal/v1/auth/login",
                new LoginRequest(request.email(), request.password(), clientContext(userAgent)),
                false);
    }

    @Override
    public AuthTokenResponse google(BrowserAuthRequests.Google request, String userAgent) {
        return exchange(
                "/internal/v1/auth/google",
                new GoogleRequest(request.credential(), clientContext(userAgent)),
                true);
    }

    @Override
    public AuthTokenResponse refresh(UUID authSessionId, String refreshToken, String userAgent) {
        if (authSessionId == null || refreshToken == null || refreshToken.isBlank()) {
            throw invalidSession();
        }
        return exchange(
                "/internal/v1/auth/refresh",
                new RefreshRequest(authSessionId, refreshToken),
                false);
    }

    @Override
    public Instant reauthenticate(
            UUID currentAuthSessionId,
            UUID userId,
            BrowserAuthRequests.Reauthenticate reauthentication) {
        if (currentAuthSessionId == null || userId == null || reauthentication == null) {
            throw invalidSession();
        }
        try {
            ReauthenticateResponse response = request("/internal/v1/auth/reauthenticate")
                    .body(new ReauthenticateRequest(
                            currentAuthSessionId,
                            userId,
                            reauthentication.method(),
                            reauthentication.password(),
                            reauthentication.credential()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (authRequest, authResponse) -> {
                        throw mapControlError(
                                authResponse.getStatusCode(),
                                authResponse.getBody());
                    })
                    .body(ReauthenticateResponse.class);
            if (response == null || response.authenticatedAt() == null) {
                throw invalidResponse();
            }
            return response.authenticatedAt();
        } catch (BffAuthException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    @Override
    public void revokeAll(
            UUID currentAuthSessionId,
            UUID userId,
            Instant authenticatedAt) {
        if (currentAuthSessionId == null || userId == null || authenticatedAt == null) {
            throw invalidSession();
        }
        try {
            request("/internal/v1/auth/revoke-all")
                    .body(new RevokeAllRequest(
                            currentAuthSessionId,
                            userId,
                            "ALL_DEVICE_LOGOUT",
                            authenticatedAt.toString()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (authRequest, authResponse) -> {
                        throw mapControlError(
                                authResponse.getStatusCode(),
                                authResponse.getBody());
                    })
                    .toBodilessEntity();
        } catch (BffAuthException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    @Override
    public void revokeBestEffort(
            UUID authSessionId,
            String tokenType,
            Map<String, String> accessTokens,
            String refreshToken) {
        if (authSessionId == null) {
            return;
        }
        try {
            request("/internal/v1/auth/revoke")
                    .body(new RevokeRequest(authSessionId, "CURRENT_LOGOUT"))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ignored) {
            // The BFF session still fails closed. Durable retry/alerting is an operations release gate.
        }
    }

    private AuthTokenResponse exchange(String path, Object body, boolean google) {
        try {
            InternalTokenResponse response = request(path)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (authRequest, authResponse) -> {
                        throw mapError(authResponse.getStatusCode(), authResponse.getBody(), google);
                    })
                    .body(InternalTokenResponse.class);
            return adapt(response);
        } catch (BffAuthException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private RestClient.RequestBodySpec request(String path) {
        RestClient.RequestBodySpec request = restClient.post().uri(path);
        if (testPrincipal != null) {
            request.header(TEST_PRINCIPAL_HEADER, testPrincipal);
        }
        return request;
    }

    private AuthTokenResponse adapt(InternalTokenResponse response) {
        if (response == null
                || response.authSessionId() == null
                || response.user() == null
                || response.user().id() == null
                || isBlank(response.refreshToken())
                || isBlank(response.tokenType())
                || response.refreshExpiresIn() <= 0
                || response.accessTokens() == null) {
            throw invalidResponse();
        }
        Map<String, AuthTokenResponse.AccessToken> accessTokens = new LinkedHashMap<>();
        for (AccessTokenView token : response.accessTokens()) {
            if (token == null
                    || !REQUIRED_AUDIENCES.contains(token.audience())
                    || isBlank(token.token())
                    || token.expiresIn() <= 0
                    || accessTokens.putIfAbsent(
                                    token.audience(),
                                    new AuthTokenResponse.AccessToken(token.token(), token.expiresIn()))
                            != null) {
                throw invalidResponse();
            }
        }
        if (!accessTokens.keySet().equals(REQUIRED_AUDIENCES)
                || isBlank(response.user().email())
                || isBlank(response.user().displayName())
                || isBlank(response.user().status())) {
            throw invalidResponse();
        }
        UUID authUserId = response.user().id();
        return new AuthTokenResponse(
                AuthTokenResponse.TARGET_SCHEMA_VERSION,
                response.authSessionId(),
                accessTokens,
                response.refreshToken(),
                response.tokenType(),
                response.refreshExpiresIn(),
                new AuthTokenResponse.User(
                        authUserId,
                        "user-" + authUserId,
                        response.user().email(),
                        response.user().displayName(),
                        response.user().pictureUrl(),
                        response.user().status()));
    }

    private BffAuthException mapError(
            HttpStatusCode status, java.io.InputStream responseBody, boolean google) {
        InternalError error = readError(responseBody);
        String code = error == null ? "" : error.code();
        return switch (code) {
            case "EMAIL_ALREADY_REGISTERED" -> BffAuthException.of(
                    HttpStatus.CONFLICT, code, "이미 가입된 이메일입니다.");
            case "INVALID_REQUEST" -> BffAuthException.of(
                    HttpStatus.BAD_REQUEST, code, "요청값이 잘못되었습니다.");
            case "INVALID_CREDENTIALS" -> google
                    ? BffAuthException.of(
                            HttpStatus.UNAUTHORIZED,
                            "GOOGLE_CREDENTIAL_INVALID",
                            "Google 로그인 정보를 확인해 주세요.")
                    : BffAuthException.of(
                            HttpStatus.UNAUTHORIZED, code, "이메일 또는 비밀번호를 확인해 주세요.");
            case "REFRESH_TOKEN_INVALID", "AUTH_SESSION_REVOKED", "REFRESH_REUSE_DETECTED" ->
                    invalidSession();
            default -> status.is5xxServerError()
                    ? unavailable()
                    : BffAuthException.of(
                            HttpStatus.BAD_GATEWAY,
                            "AUTH_SERVICE_ERROR",
                            "인증 요청을 처리하지 못했습니다.");
        };
    }

    private BffAuthException mapControlError(
            HttpStatusCode status,
            java.io.InputStream responseBody) {
        InternalError error = readError(responseBody);
        String code = error == null ? "" : error.code();
        return switch (code) {
            case "REAUTHENTICATION_FAILED" -> BffAuthException.of(
                    HttpStatus.UNAUTHORIZED,
                    code,
                    "인증 정보를 확인해 주세요.");
            case "RECENT_AUTH_REQUIRED" -> BffAuthException.of(
                    HttpStatus.FORBIDDEN,
                    "REAUTHENTICATION_REQUIRED",
                    "모든 기기 로그아웃을 위해 다시 인증해 주세요.");
            case "AUTH_SESSION_REVOKED", "AUTH_SESSION_SUBJECT_MISMATCH" ->
                    invalidSession();
            default -> status.is5xxServerError()
                    ? unavailable()
                    : BffAuthException.of(
                            HttpStatus.BAD_GATEWAY,
                            "AUTH_SERVICE_ERROR",
                            "인증 요청을 처리하지 못했습니다.");
        };
    }

    private InternalError readError(java.io.InputStream body) {
        try {
            return objectMapper.readValue(body, InternalError.class);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private ClientContext clientContext(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new ClientContext(null);
        }
        StringBuilder value = new StringBuilder(Math.min(userAgent.length(), 256));
        for (int index = 0; index < userAgent.length() && value.length() < 256; index++) {
            char character = userAgent.charAt(index);
            if (character >= 32 && character != 127) {
                value.append(character);
            }
        }
        return new ClientContext(value.isEmpty() ? null : value.toString());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private BffAuthException invalidResponse() {
        return BffAuthException.of(
                HttpStatus.BAD_GATEWAY,
                "AUTH_SERVICE_INVALID_RESPONSE",
                "인증 요청을 처리하지 못했습니다.");
    }

    private BffAuthException unavailable() {
        return BffAuthException.of(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AUTH_SERVICE_UNAVAILABLE",
                "인증 서비스에 일시적으로 연결할 수 없습니다.");
    }

    private BffAuthException invalidSession() {
        return BffAuthException.of(
                HttpStatus.UNAUTHORIZED,
                "SESSION_INVALID",
                "로그인이 만료되었습니다. 다시 로그인해 주세요.");
    }

    private record ClientContext(String deviceLabel) {
    }

    private record SignupRequest(
            String email, String password, String displayName, ClientContext clientContext) {
    }

    private record LoginRequest(String email, String password, ClientContext clientContext) {
    }

    private record GoogleRequest(String credential, ClientContext clientContext) {
    }

    private record RefreshRequest(UUID authSessionId, String refreshToken) {
    }

    private record RevokeRequest(UUID authSessionId, String reason) {
    }

    private record ReauthenticateRequest(
            UUID currentAuthSessionId,
            UUID userId,
            String method,
            String password,
            String credential) {
    }

    private record ReauthenticateResponse(Instant authenticatedAt) {
    }

    private record RevokeAllRequest(
            UUID currentAuthSessionId,
            UUID userId,
            String reason,
            String authenticatedAt) {
    }

    private record InternalTokenResponse(
            List<AccessTokenView> accessTokens,
            String refreshToken,
            String tokenType,
            long refreshExpiresIn,
            UUID authSessionId,
            InternalUser user) {
    }

    private record AccessTokenView(String audience, String token, long expiresIn) {
    }

    private record InternalUser(
            UUID id, String email, String displayName, String pictureUrl, String status) {
    }

    private record InternalError(String code, String message) {
    }
}
