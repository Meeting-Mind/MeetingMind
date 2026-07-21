package com.meetingmind.bff.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.bff.tokenvault.TokenBundlePayload;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Target Auth client enabled only after the BFF switches from compatibility mode. */
public final class TargetAuthServiceClient implements CompatibilityAuthClient {

    private static final Set<String> AUDIENCES = Set.of(
            "meetingmind-core", "meetingmind-ai", "meetingmind-livekit");

    private final RestClient authClient;
    private final RestClient coreClient;
    private final ObjectMapper objectMapper;
    private final String testWorkloadPrincipal;

    public TargetAuthServiceClient(
            RestClient authClient,
            RestClient coreClient,
            ObjectMapper objectMapper,
            String testWorkloadPrincipal) {
        this.authClient = authClient;
        this.coreClient = coreClient;
        this.objectMapper = objectMapper;
        this.testWorkloadPrincipal = testWorkloadPrincipal == null ? "" : testWorkloadPrincipal.trim();
    }

    @Override
    public LegacyAuthTokenResponse signup(BrowserAuthRequests.Signup request, String userAgent) {
        return exchange("/internal/v1/auth/signup", new SignupRequest(
                request.email(), request.password(), request.displayName(), clientContext(userAgent)), false);
    }

    @Override
    public LegacyAuthTokenResponse login(BrowserAuthRequests.Login request, String userAgent) {
        return exchange("/internal/v1/auth/login", new LoginRequest(
                request.email(), request.password(), clientContext(userAgent)), false);
    }

    @Override
    public LegacyAuthTokenResponse google(BrowserAuthRequests.Google request, String userAgent) {
        return exchange("/internal/v1/auth/google", new GoogleRequest(
                request.credential(), clientContext(userAgent)), true);
    }

    @Override
    public LegacyAuthTokenResponse refresh(String refreshToken, String userAgent) {
        throw sessionInvalid();
    }

    @Override
    public LegacyAuthTokenResponse refresh(UUID authSessionId, String refreshToken, String userAgent) {
        if (authSessionId == null || isBlank(refreshToken)) {
            throw sessionInvalid();
        }
        return exchange("/internal/v1/auth/refresh", new RefreshRequest(authSessionId, refreshToken), false);
    }

    @Override
    public void revokeBestEffort(String tokenType, String accessToken, String refreshToken) {
        // Target Auth revoke requires the server-side AuthSession ID.
    }

    @Override
    public void revokeBestEffort(UUID authSessionId, String tokenType, String accessToken, String refreshToken) {
        if (authSessionId == null) {
            return;
        }
        try {
            request(authClient.post().uri("/internal/v1/auth/revoke"))
                    .body(new RevokeRequest(authSessionId, "CURRENT_LOGOUT"))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ignored) {
            // BFF local session invalidation remains fail closed.
        }
    }

    @Override
    public void projectUser(LegacyAuthTokenResponse tokens) {
        TokenBundlePayload.AccessToken coreToken = tokens.accessTokens().get("meetingmind-core");
        if (coreToken == null || tokens.user() == null) {
            throw invalidResponse();
        }
        project(
                BffAuthUser.from(tokens.user()),
                tokens.tokenType(),
                coreToken.token());
    }

    @Override
    public void projectProfile(BffAuthUser user, String tokenType, String coreAccessToken) {
        project(user, tokenType, coreAccessToken);
    }

    @Override
    public void prepareWithdrawal(String tokenType, String coreAccessToken) {
        executeCoreWithdrawal("/internal/v1/core/account-withdrawal/reservation", tokenType, coreAccessToken);
    }

    @Override
    public void completeWithdrawal(String tokenType, String coreAccessToken) {
        executeCoreWithdrawal("/internal/v1/core/account-withdrawal/complete", tokenType, coreAccessToken);
    }

    @Override
    public void cancelWithdrawal(String tokenType, String coreAccessToken) {
        executeCoreWithdrawal("/internal/v1/core/account-withdrawal/cancel", tokenType, coreAccessToken);
    }

    @Override
    public void withdraw(UUID authSessionId, UUID userId, Instant authenticatedAt) {
        executeAccountCommand(
                "/internal/v1/auth/withdrawal",
                new WithdrawalRequest(authSessionId, userId, authenticatedAt));
    }

    private void project(BffAuthUser user, String tokenType, String coreAccessToken) {
        if (user == null || isBlank(tokenType) || isBlank(coreAccessToken)) {
            throw invalidResponse();
        }
        try {
            request(coreClient.post().uri("/internal/v1/core/auth-users/projection"))
                    .header(HttpHeaders.AUTHORIZATION, tokenType + " " + coreAccessToken)
                    .body(new ProjectionRequest(
                            user.email(), user.displayName(), user.pictureUrl()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw unavailable();
                    })
                    .toBodilessEntity();
        } catch (BffAuthException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private void executeCoreWithdrawal(String path, String tokenType, String coreAccessToken) {
        if (isBlank(tokenType) || isBlank(coreAccessToken)) {
            throw invalidResponse();
        }
        try {
            request(coreClient.post().uri(path))
                    .header(HttpHeaders.AUTHORIZATION, tokenType + " " + coreAccessToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, error) -> {
                        throw mapCoreWithdrawalError(error.getStatusCode(), error.getBody());
                    })
                    .toBodilessEntity();
        } catch (BffAuthException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    @Override
    public void reauthenticate(UUID authSessionId, UUID userId, String password, String googleCredential) {
        if (authSessionId == null || userId == null || (hasText(password) == hasText(googleCredential))) {
            throw BffAuthException.of(HttpStatus.FORBIDDEN, "REAUTHENTICATION_REQUIRED", "최근 인증이 필요합니다.");
        }
        try {
            request(authClient.post().uri("/internal/v1/auth/re-authenticate"))
                    .body(new ReauthenticateRequest(authSessionId, userId, password, googleCredential))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, error) -> {
                        throw mapError(error.getStatusCode(), error.getBody(), hasText(googleCredential));
                    })
                    .toBodilessEntity();
        } catch (BffAuthException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    @Override
    public void revokeAll(UUID authSessionId, UUID userId, Instant authenticatedAt) {
        if (authSessionId == null || userId == null || authenticatedAt == null) {
            throw BffAuthException.of(HttpStatus.FORBIDDEN, "REAUTHENTICATION_REQUIRED", "최근 인증이 필요합니다.");
        }
        try {
            request(authClient.post().uri("/internal/v1/auth/revoke-all"))
                    .body(new RevokeAllRequest(authSessionId, userId, "ALL_DEVICE_LOGOUT", authenticatedAt))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, error) -> {
                        throw mapError(error.getStatusCode(), error.getBody(), false);
                    })
                    .toBodilessEntity();
        } catch (BffAuthException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    @Override
    public boolean requestPasswordReset(String email, String requestIpPrefix) {
        try {
            request(authClient.post().uri("/internal/v1/auth/password-reset-requests"))
                    .body(new PasswordResetRequest(email, requestIpPrefix))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, error) -> {
                        throw mapError(error.getStatusCode(), error.getBody(), false);
                    })
                    .toBodilessEntity();
            return true;
        } catch (BffAuthException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        executeAccountCommand(
                "/internal/v1/auth/password-resets", new PasswordResetConfirmRequest(token, newPassword));
    }

    @Override
    public void changePassword(UUID authSessionId, UUID userId, String currentPassword, String newPassword) {
        executeAccountCommand(
                "/internal/v1/auth/password",
                new PasswordChangeRequest(authSessionId, userId, currentPassword, newPassword));
    }

    @Override
    public BffAuthUser updateProfile(UUID userId, String displayName) {
        try {
            TargetUser response = request(authClient.patch().uri("/internal/v1/auth/profile"))
                    .body(new ProfileUpdateRequest(userId, displayName))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, error) -> {
                        throw mapError(error.getStatusCode(), error.getBody(), false);
                    })
                    .body(TargetUser.class);
            if (response == null
                    || response.id() == null
                    || isBlank(response.email())
                    || isBlank(response.displayName())
                    || isBlank(response.status())
                    || !userId.equals(response.id())) {
                throw invalidResponse();
            }
            return new BffAuthUser(
                    response.id().toString(),
                    response.email(),
                    response.displayName(),
                    response.pictureUrl(),
                    response.status());
        } catch (BffAuthException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    @Override
    public BffAuthUser updateProfileImage(UUID userId, String contentType, String filename, byte[] bytes) {
        try {
            MultipartBodyBuilder body = new MultipartBodyBuilder();
            body.part("userId", userId.toString());
            body.part("image", new NamedByteArrayResource(bytes, filename))
                    .contentType(MediaType.parseMediaType(contentType));
            TargetUser response = request(authClient.post().uri("/internal/v1/auth/profile-image"))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, error) -> {
                        throw mapError(error.getStatusCode(), error.getBody(), false);
                    })
                    .body(TargetUser.class);
            if (response == null
                    || response.id() == null
                    || isBlank(response.email())
                    || isBlank(response.displayName())
                    || isBlank(response.status())
                    || !userId.equals(response.id())) {
                throw invalidResponse();
            }
            return new BffAuthUser(
                    response.id().toString(),
                    response.email(),
                    response.displayName(),
                    response.pictureUrl(),
                    response.status());
        } catch (BffAuthException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw unavailable();
        }
    }

    private void executeAccountCommand(String path, Object body) {
        try {
            request(authClient.post().uri(path))
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, error) -> {
                        throw mapError(error.getStatusCode(), error.getBody(), false);
                    })
                    .toBodilessEntity();
        } catch (BffAuthException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private LegacyAuthTokenResponse exchange(String path, Object body, boolean google) {
        try {
            TargetTokenResponse response = request(authClient.post().uri(path))
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, error) -> {
                        throw mapError(error.getStatusCode(), error.getBody(), google);
                    })
                    .body(TargetTokenResponse.class);
            return validate(response);
        } catch (BffAuthException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private RestClient.RequestBodySpec request(RestClient.RequestBodySpec request) {
        return testWorkloadPrincipal.isBlank()
                ? request
                : request.header("X-MeetingMind-Test-Principal", testWorkloadPrincipal);
    }

    private LegacyAuthTokenResponse validate(TargetTokenResponse response) {
        if (response == null
                || response.authSessionId() == null
                || isBlank(response.refreshToken())
                || isBlank(response.tokenType())
                || response.refreshExpiresIn() <= 0
                || response.user() == null
                || response.user().id() == null
                || isBlank(response.user().email())
                || isBlank(response.user().displayName())
                || isBlank(response.user().status())
                || response.accessTokens() == null) {
            throw invalidResponse();
        }
        Instant now = Instant.now();
        Map<String, TokenBundlePayload.AccessToken> tokens = response.accessTokens().stream().collect(Collectors.toMap(
                TargetAccessToken::audience,
                token -> new TokenBundlePayload.AccessToken(token.token(), now.plusSeconds(token.expiresIn())),
                (first, duplicate) -> {
                    throw invalidResponse();
                }
        ));
        if (!tokens.keySet().equals(AUDIENCES)
                || tokens.values().stream().anyMatch(token -> isBlank(token.token()) || !token.expiresAt().isAfter(now))) {
            throw invalidResponse();
        }
        TokenBundlePayload.AccessToken core = tokens.get("meetingmind-core");
        return new LegacyAuthTokenResponse(
                core.token(),
                response.refreshToken(),
                response.tokenType(),
                600,
                response.refreshExpiresIn(),
                new LegacyAuthUser(
                        response.user().id().toString(),
                        response.user().email(),
                        response.user().displayName(),
                        response.user().pictureUrl(),
                        response.user().status()),
                response.authSessionId(),
                tokens);
    }

    private BffAuthException mapError(HttpStatusCode status, java.io.InputStream body, boolean google) {
        String code = "";
        try {
            TargetError error = objectMapper.readValue(body, TargetError.class);
            code = error == null ? "" : error.code();
        } catch (IOException | RuntimeException ignored) {
            // Error normalization must not expose upstream payloads.
        }
        if ("EMAIL_ALREADY_REGISTERED".equals(code)) {
            return BffAuthException.of(HttpStatus.CONFLICT, code, "이미 가입된 이메일입니다.");
        }
        if ("INVALID_REQUEST".equals(code)) {
            return BffAuthException.of(HttpStatus.BAD_REQUEST, code, "요청값이 잘못되었습니다.");
        }
        if ("INVALID_CREDENTIALS".equals(code) || "GOOGLE_CREDENTIAL_INVALID".equals(code)) {
            return BffAuthException.of(
                    HttpStatus.UNAUTHORIZED,
                    google ? "GOOGLE_CREDENTIAL_INVALID" : "INVALID_CREDENTIALS",
                    google ? "Google 로그인 정보를 확인해 주세요." : "이메일 또는 비밀번호를 확인해 주세요.");
        }
        if ("REFRESH_TOKEN_INVALID".equals(code)
                || "AUTH_SESSION_REVOKED".equals(code)
                || "REFRESH_REUSE_DETECTED".equals(code)) {
            return sessionInvalid();
        }
        if ("RECENT_AUTH_REQUIRED".equals(code)) {
            return BffAuthException.of(HttpStatus.FORBIDDEN, "REAUTHENTICATION_REQUIRED", "최근 인증이 필요합니다.");
        }
        if ("PASSWORD_RESET_TOKEN_INVALID".equals(code)
                || "PASSWORD_REUSE_FORBIDDEN".equals(code)) {
            return BffAuthException.of(HttpStatus.BAD_REQUEST, code, "비밀번호 요청을 처리할 수 없습니다.");
        }
        if ("LOCAL_CREDENTIAL_REQUIRED".equals(code)) {
            return BffAuthException.of(HttpStatus.CONFLICT, code, "local 비밀번호가 설정된 계정만 변경할 수 있습니다.");
        }
        if ("PROFILE_IMAGE_INVALID".equals(code)) {
            return BffAuthException.of(HttpStatus.BAD_REQUEST, code, "프로필 사진 파일을 확인해 주세요.");
        }
        if ("PROFILE_IMAGE_STORAGE_UNAVAILABLE".equals(code)) {
            return unavailable();
        }
        return status.is5xxServerError() ? unavailable() : invalidResponse();
    }

    private BffAuthException mapCoreWithdrawalError(HttpStatusCode status, java.io.InputStream body) {
        String code = "";
        try {
            TargetError error = objectMapper.readValue(body, TargetError.class);
            code = error == null ? "" : error.code();
        } catch (IOException | RuntimeException ignored) {
            // Core error payloads are normalized below.
        }
        if ("SPACE_OWNER_TRANSFER_REQUIRED".equals(code)) {
            return BffAuthException.of(HttpStatus.CONFLICT, code, "OWNER 권한을 이양하거나 Space를 삭제해야 합니다.");
        }
        if ("ACCOUNT_WITHDRAWAL_PENDING".equals(code) || "WITHDRAWAL_RESERVATION_INVALID".equals(code)) {
            return BffAuthException.of(HttpStatus.CONFLICT, code, "계정 탈퇴 상태를 처리할 수 없습니다.");
        }
        return status.is5xxServerError() ? unavailable() : invalidResponse();
    }

    private static ClientContext clientContext(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return new ClientContext(userAgent.substring(0, Math.min(userAgent.length(), 256)));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static BffAuthException invalidResponse() {
        return BffAuthException.of(HttpStatus.BAD_GATEWAY, "AUTH_SERVICE_INVALID_RESPONSE", "인증 요청을 처리하지 못했습니다.");
    }

    private static BffAuthException unavailable() {
        return BffAuthException.of(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_SERVICE_UNAVAILABLE", "인증 서비스에 일시적으로 연결할 수 없습니다.");
    }

    private static BffAuthException sessionInvalid() {
        return BffAuthException.of(HttpStatus.UNAUTHORIZED, "SESSION_INVALID", "로그인이 만료되었습니다. 다시 로그인해 주세요.");
    }

    private record ClientContext(String deviceLabel) {}
    private record SignupRequest(String email, String password, String displayName, ClientContext clientContext) {}
    private record LoginRequest(String email, String password, ClientContext clientContext) {}
    private record GoogleRequest(String credential, ClientContext clientContext) {}
    private record RefreshRequest(UUID authSessionId, String refreshToken) {}
    private record RevokeRequest(UUID authSessionId, String reason) {}
    private record ReauthenticateRequest(UUID currentAuthSessionId, UUID userId, String password, String googleCredential) {}
    private record RevokeAllRequest(UUID currentAuthSessionId, UUID userId, String reason, Instant authenticatedAt) {}
    private record PasswordResetRequest(String email, String requestIpPrefix) {}
    private record PasswordResetConfirmRequest(String token, String newPassword) {}
    private record PasswordChangeRequest(UUID currentAuthSessionId, UUID userId, String currentPassword, String newPassword) {}
    private record ProfileUpdateRequest(UUID userId, String displayName) {}
    private record WithdrawalRequest(UUID currentAuthSessionId, UUID userId, Instant authenticatedAt) {}
    private record ProjectionRequest(String email, String displayName, String pictureUrl) {}
    private record TargetTokenResponse(List<TargetAccessToken> accessTokens, String refreshToken, String tokenType,
                                       long refreshExpiresIn, UUID authSessionId, TargetUser user) {}
    private record TargetAccessToken(String audience, String token, long expiresIn) {}
    private record TargetUser(UUID id, String email, String displayName, String pictureUrl, String status) {}
    private record TargetError(String code) {}

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] bytes, String filename) {
            super(bytes == null ? new byte[0] : bytes);
            this.filename = filename == null || filename.isBlank() ? "profile-image" : filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
