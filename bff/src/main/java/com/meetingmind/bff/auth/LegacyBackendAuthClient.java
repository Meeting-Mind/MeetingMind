package com.meetingmind.bff.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public final class LegacyBackendAuthClient implements AuthClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyBackendAuthClient.class);

    private static final String SIGNUP_PATH = "/api/v1/auth/signup";
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String GOOGLE_PATH = "/api/v1/auth/google";
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public LegacyBackendAuthClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public AuthTokenResponse signup(BrowserAuthRequests.Signup request, String userAgent) {
        return adapt(exchange(
                SIGNUP_PATH,
                new LegacySignupRequest(request.email(), request.password(), request.displayName()),
                userAgent,
                false));
    }

    @Override
    public AuthTokenResponse login(BrowserAuthRequests.Login request, String userAgent) {
        return adapt(exchange(
                LOGIN_PATH,
                new LegacyLoginRequest(request.email(), request.password()),
                userAgent,
                false));
    }

    @Override
    public AuthTokenResponse google(BrowserAuthRequests.Google request, String userAgent) {
        return adapt(exchange(
                GOOGLE_PATH,
                new LegacyGoogleRequest(request.credential()),
                userAgent,
                true));
    }

    @Override
    public AuthTokenResponse refresh(UUID authSessionId, String refreshToken, String userAgent) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw BffAuthException.of(
                    HttpStatus.UNAUTHORIZED,
                    "REFRESH_TOKEN_INVALID",
                    "로그인이 만료되었습니다. 다시 로그인해 주세요.");
        }
        AuthTokenResponse refreshed = adapt(exchange(
                REFRESH_PATH, new LegacyRefreshRequest(refreshToken), userAgent, false));
        return new AuthTokenResponse(
                refreshed.schemaVersion(),
                authSessionId,
                refreshed.accessTokens(),
                refreshed.refreshToken(),
                refreshed.tokenType(),
                refreshed.refreshExpiresIn(),
                refreshed.user());
    }

    @Override
    public Instant reauthenticate(
            UUID currentAuthSessionId,
            UUID userId,
            BrowserAuthRequests.Reauthenticate request) {
        throw allDeviceLogoutUnavailable();
    }

    @Override
    public void revokeAll(UUID currentAuthSessionId, UUID userId, Instant authenticatedAt) {
        throw allDeviceLogoutUnavailable();
    }

    @Override
    public void revokeBestEffort(
            UUID authSessionId,
            String tokenType,
            Map<String, String> accessTokens,
            String refreshToken) {
        String legacyAccess = accessTokens.get(AuthTokenResponse.LEGACY_AUDIENCE);
        if (legacyAccess == null) {
            LOGGER.warn("event=compat_auth_revoke_skipped reason=legacy_access_missing");
            return;
        }
        try {
            restClient.post()
                    .uri(LOGOUT_PATH)
                    .header(HttpHeaders.AUTHORIZATION, tokenType + " " + legacyAccess)
                    .body(new LegacyLogoutRequest(refreshToken))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException exception) {
            LOGGER.warn("event=compat_auth_revoke_failed outcome=local_session_invalidated");
        }
    }

    private LegacyTokenResponse exchange(String path, Object body, String userAgent, boolean google) {
        try {
            RestClient.RequestBodySpec request = restClient.post().uri(path);
            String sanitizedUserAgent = sanitizeUserAgent(userAgent);
            if (!sanitizedUserAgent.isEmpty()) {
                request.header(HttpHeaders.USER_AGENT, sanitizedUserAgent);
            }
            LegacyTokenResponse response = request.body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (backendRequest, backendResponse) -> {
                        throw mapBackendError(backendResponse.getStatusCode(), backendResponse.getBody(), google);
                    })
                    .body(LegacyTokenResponse.class);
            return validate(response);
        } catch (BffAuthException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private BffAuthException mapBackendError(
            HttpStatusCode status, java.io.InputStream responseBody, boolean google) {
        LegacyAuthError backendError = readBackendError(responseBody);
        String code = backendError == null ? "" : backendError.code();
        if ("EMAIL_ALREADY_REGISTERED".equals(code)) {
            return BffAuthException.of(
                    HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "이미 가입된 이메일입니다.");
        }
        if ("INVALID_REQUEST".equals(code)) {
            return BffAuthException.of(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청값이 잘못되었습니다.");
        }
        if ("INVALID_CREDENTIALS".equals(code)) {
            return google
                    ? BffAuthException.of(
                            HttpStatus.UNAUTHORIZED,
                            "GOOGLE_CREDENTIAL_INVALID",
                            "Google 로그인 정보를 확인해 주세요.")
                    : BffAuthException.of(
                            HttpStatus.UNAUTHORIZED,
                            "INVALID_CREDENTIALS",
                            "이메일 또는 비밀번호를 확인해 주세요.");
        }
        if ("REFRESH_TOKEN_INVALID".equals(code)) {
            return BffAuthException.of(
                    HttpStatus.UNAUTHORIZED,
                    "REFRESH_TOKEN_INVALID",
                    "로그인이 만료되었습니다. 다시 로그인해 주세요.");
        }
        return status.is5xxServerError()
                ? unavailable()
                : BffAuthException.of(
                        HttpStatus.BAD_GATEWAY,
                        "AUTH_SERVICE_ERROR",
                        "인증 요청을 처리하지 못했습니다.");
    }

    private LegacyAuthError readBackendError(java.io.InputStream responseBody) {
        try {
            return objectMapper.readValue(responseBody, LegacyAuthError.class);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private LegacyTokenResponse validate(LegacyTokenResponse response) {
        if (response == null
                || isBlank(response.accessToken())
                || isBlank(response.refreshToken())
                || isBlank(response.tokenType())
                || response.expiresIn() <= 0
                || response.refreshExpiresIn() <= response.expiresIn()
                || response.user() == null
                || isBlank(response.user().id())
                || isBlank(response.user().email())
                || isBlank(response.user().displayName())
                || isBlank(response.user().status())) {
            throw BffAuthException.of(
                    HttpStatus.BAD_GATEWAY,
                    "AUTH_SERVICE_INVALID_RESPONSE",
                    "인증 요청을 처리하지 못했습니다.");
        }
        return response;
    }

    private AuthTokenResponse adapt(LegacyTokenResponse response) {
        UUID authUserId = parseCanonicalResourceUserId(response.user().id());
        return new AuthTokenResponse(
                AuthTokenResponse.LEGACY_SCHEMA_VERSION,
                UUID.randomUUID(),
                Map.of(
                        AuthTokenResponse.LEGACY_AUDIENCE,
                        new AuthTokenResponse.AccessToken(response.accessToken(), response.expiresIn())),
                response.refreshToken(),
                response.tokenType(),
                response.refreshExpiresIn(),
                new AuthTokenResponse.User(
                        authUserId,
                        response.user().id(),
                        response.user().email(),
                        response.user().displayName(),
                        response.user().pictureUrl(),
                        response.user().status()));
    }

    private UUID parseCanonicalResourceUserId(String value) {
        if (value == null || !value.startsWith("user-")) {
            throw invalidResponse();
        }
        try {
            UUID authUserId = UUID.fromString(value.substring("user-".length()));
            if (!value.equals("user-" + authUserId)) {
                throw invalidResponse();
            }
            return authUserId;
        } catch (IllegalArgumentException exception) {
            throw invalidResponse();
        }
    }

    private String sanitizeUserAgent(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder sanitized = new StringBuilder(Math.min(value.length(), 256));
        for (int index = 0; index < value.length() && sanitized.length() < 256; index++) {
            char character = value.charAt(index);
            if (character >= 32 && character != 127) {
                sanitized.append(character);
            }
        }
        return sanitized.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private BffAuthException unavailable() {
        return BffAuthException.of(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AUTH_SERVICE_UNAVAILABLE",
                "인증 서비스에 일시적으로 연결할 수 없습니다.");
    }

    private BffAuthException allDeviceLogoutUnavailable() {
        return BffAuthException.of(
                HttpStatus.CONFLICT,
                "AUTH_FEATURE_UNAVAILABLE",
                "현재 인증 모드에서는 모든 기기 로그아웃을 사용할 수 없습니다.");
    }

    private BffAuthException invalidResponse() {
        return BffAuthException.of(
                HttpStatus.BAD_GATEWAY,
                "AUTH_SERVICE_INVALID_RESPONSE",
                "인증 요청을 처리하지 못했습니다.");
    }

    private record LegacySignupRequest(String email, String password, String displayName) {}

    private record LegacyLoginRequest(String email, String password) {}

    private record LegacyGoogleRequest(String credential) {}

    private record LegacyRefreshRequest(String refreshToken) {}

    private record LegacyLogoutRequest(String refreshToken) {}

    private record LegacyAuthError(String code, String message) {}

    private record LegacyTokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            long refreshExpiresIn,
            LegacyUser user) {
    }

    private record LegacyUser(
            String id,
            String email,
            String displayName,
            String pictureUrl,
            String status) {
    }
}
