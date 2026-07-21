package com.meetingmind.auth.runtime;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class AuthRuntimeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthRuntimeService.class);
    private static final Set<String> REQUIRED_AUDIENCES = Set.of(
            "meetingmind-core",
            "meetingmind-ai",
            "meetingmind-livekit"
    );
    private static final Duration FUTURE_AUTHENTICATION_SKEW = Duration.ofSeconds(60);

    private final JdbcAuthRepository repository;
    private final PasswordSupport passwords;
    private final RefreshTokenSupport refreshTokens;
    private final GoogleCredentialVerifier googleVerifier;
    private final AccessTokenIssuer accessTokenIssuer;
    private final AuthAuditRecorder auditRecorder;
    private final AuthRuntimeProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;

    AuthRuntimeService(
            JdbcAuthRepository repository,
            PasswordSupport passwords,
            RefreshTokenSupport refreshTokens,
            GoogleCredentialVerifier googleVerifier,
            AccessTokenIssuer accessTokenIssuer,
            AuthAuditRecorder auditRecorder,
            AuthRuntimeProperties properties,
            TransactionTemplate transactions,
            Clock clock
    ) {
        this.repository = repository;
        this.passwords = passwords;
        this.refreshTokens = refreshTokens;
        this.googleVerifier = googleVerifier;
        this.accessTokenIssuer = accessTokenIssuer;
        this.auditRecorder = auditRecorder;
        this.properties = properties;
        this.transactions = transactions;
        this.clock = clock;
    }

    AuthApiModels.TokenResponse signup(AuthApiModels.SignupRequest request, String traceId) {
        if (!passwords.isValid(request.password())) {
            throw AuthRuntimeException.badRequest("INVALID_REQUEST", PasswordSupport.POLICY_MESSAGE);
        }
        String email = JdbcAuthRepository.canonicalEmail(request.email());
        String displayName = request.displayName().trim();
        try {
            return transactions.execute(status -> {
                if (repository.findUserByEmail(email).isPresent()) {
                    throw emailAlreadyRegistered();
                }
                Instant now = Instant.now(clock);
                AuthModels.User user = repository.insertUser(
                        UUID.randomUUID(),
                        email,
                        displayName,
                        null,
                        now
                );
                repository.insertIdentity(
                        UUID.randomUUID(),
                        user.id(),
                        "LOCAL",
                        email,
                        passwords.hash(request.password()),
                        now
                );
                return issueSession(user, deviceLabel(request.clientContext()), now, traceId, "SIGNUP_SUCCESS");
            });
        } catch (DataIntegrityViolationException exception) {
            throw emailAlreadyRegistered();
        }
    }

    AuthApiModels.TokenResponse login(AuthApiModels.LoginRequest request, String traceId) {
        String email = JdbcAuthRepository.canonicalEmail(request.email());
        Optional<AuthApiModels.TokenResponse> result = transactions.execute(status -> {
            AuthModels.Identity identity = repository.findIdentity("LOCAL", email).orElse(null);
            if (identity == null || !passwords.matches(request.password(), identity.passwordHash())) {
                return Optional.empty();
            }
            AuthModels.User user = repository.findUserById(identity.userId()).orElse(null);
            if (user == null || !user.isActive()) {
                return Optional.empty();
            }
            Instant now = Instant.now(clock);
            repository.touchIdentityAndUser(identity, now);
            AuthModels.User updated = repository.findUserById(user.id()).orElseThrow();
            return Optional.of(issueSession(
                    updated,
                    deviceLabel(request.clientContext()),
                    now,
                    traceId,
                    "LOGIN_SUCCESS"
            ));
        });
        if (result == null || result.isEmpty()) {
            auditRecorder.failure("LOGIN_FAILURE", "INVALID_CREDENTIALS", traceId);
            throw invalidCredentials();
        }
        return result.get();
    }

    AuthApiModels.TokenResponse google(AuthApiModels.GoogleRequest request, String traceId) {
        AuthModels.GoogleUser googleUser;
        try {
            googleUser = googleVerifier.verify(request.credential());
            validateGoogleUser(googleUser);
        } catch (AuthRuntimeException exception) {
            auditRecorder.failure("GOOGLE_LOGIN_FAILURE", exception.code(), traceId);
            throw exception;
        }
        try {
            return transactions.execute(status -> {
                Instant now = Instant.now(clock);
                repository.lockGoogleSubject(googleUser.providerUserId());
                AuthModels.Identity identity = repository.findIdentity(
                        "GOOGLE",
                        googleUser.providerUserId()
                ).orElse(null);
                AuthModels.User user;
                if (identity != null) {
                    repository.touchIdentityAndUser(identity, now);
                    user = repository.findUserById(identity.userId()).orElseThrow(
                            AuthRuntimeService::invalidCredentials
                    );
                } else {
                    user = repository.upsertGoogleUser(
                            UUID.randomUUID(),
                            googleUser.email(),
                            boundedDisplayName(googleUser.displayName(), googleUser.email()),
                            googleUser.pictureUrl(),
                            now
                    );
                    repository.insertGoogleIdentity(
                            UUID.randomUUID(),
                            user.id(),
                            googleUser.providerUserId(),
                            now
                    );
                }
                if (!user.isActive()) {
                    throw invalidCredentials();
                }
                return issueSession(
                        user,
                        deviceLabel(request.clientContext()),
                        now,
                        traceId,
                        "GOOGLE_LOGIN_SUCCESS"
                );
            });
        } catch (DataIntegrityViolationException exception) {
            LOGGER.warn(
                    "google_account_link_conflict constraint={} sql_state={}",
                    knownConstraint(exception),
                    sqlState(exception)
            );
            auditRecorder.failure("GOOGLE_LOGIN_FAILURE", "GOOGLE_ACCOUNT_LINK_CONFLICT", traceId);
            throw AuthRuntimeException.conflict(
                    "GOOGLE_ACCOUNT_LINK_CONFLICT",
                    "Google 계정을 안전하게 연결할 수 없습니다."
            );
        }
    }

    AuthApiModels.TokenResponse refresh(AuthApiModels.RefreshRequest request, String traceId) {
        String tokenHash = refreshTokens.hash(request.refreshToken());
        RefreshOutcome outcome = transactions.execute(status -> {
            AuthModels.RefreshState state = repository.findRefreshStateForUpdate(tokenHash).orElse(null);
            if (state == null || !state.session().id().equals(request.authSessionId())) {
                return RefreshOutcome.error(RefreshResult.INVALID);
            }
            Instant now = Instant.now(clock);
            AuthModels.Credential credential = state.credential();
            AuthModels.Session session = state.session();

            if (credential.isUsed()) {
                repository.revokeSession(
                        session,
                        "REFRESH_REUSE",
                        now,
                        denyUntil(now),
                        traceId
                );
                repository.insertAudit(
                        session.userId(),
                        session.id(),
                        "REFRESH_REUSE",
                        "REFRESH_REUSE_DETECTED",
                        now,
                        traceId,
                        Map.of()
                );
                return RefreshOutcome.error(RefreshResult.REUSE);
            }
            if (session.isRevoked() || credential.isRevoked()) {
                return RefreshOutcome.error(RefreshResult.REVOKED);
            }
            if (session.isExpired(now) || credential.isExpired(now)) {
                repository.revokeSession(session, "EXPIRED", now, denyUntil(now), traceId);
                repository.insertAudit(
                        session.userId(),
                        session.id(),
                        "SESSION_REVOKED",
                        "EXPIRED",
                        now,
                        traceId,
                        Map.of()
                );
                return RefreshOutcome.error(RefreshResult.EXPIRED);
            }
            if (!state.user().isActive()) {
                repository.revokeSession(session, "USER_DISABLED", now, denyUntil(now), traceId);
                return RefreshOutcome.error(RefreshResult.REVOKED);
            }

            String replacementToken = refreshTokens.issue();
            UUID replacementId = UUID.randomUUID();
            List<AuthApiModels.AccessTokenView> accessTokens = issueAccessTokens(
                    state.user(),
                    session.id(),
                    now
            );
            repository.rotateCredential(
                    credential,
                    replacementId,
                    refreshTokens.hash(replacementToken),
                    now,
                    session.expiresAt()
            );
            repository.insertAudit(
                    session.userId(),
                    session.id(),
                    "REFRESH_SUCCESS",
                    null,
                    now,
                    traceId,
                    Map.of()
            );
            return RefreshOutcome.success(tokenResponse(
                    state.user(),
                    session.id(),
                    replacementToken,
                    session.expiresAt(),
                    now,
                    accessTokens
            ));
        });

        if (outcome == null || outcome.result() == RefreshResult.INVALID) {
            auditRecorder.failure("REFRESH_FAILURE", "REFRESH_TOKEN_INVALID", traceId);
            throw AuthRuntimeException.unauthorized(
                    "REFRESH_TOKEN_INVALID",
                    "refresh token이 올바르지 않습니다."
            );
        }
        return switch (outcome.result()) {
            case SUCCESS -> outcome.response();
            case REUSE -> throw AuthRuntimeException.conflict(
                    "REFRESH_REUSE_DETECTED",
                    "refresh token 재사용을 감지해 현재 인증 세션을 폐기했습니다."
            );
            case REVOKED, EXPIRED -> throw AuthRuntimeException.unauthorized(
                    "AUTH_SESSION_REVOKED",
                    "인증 세션이 만료되었거나 폐기되었습니다."
            );
            case INVALID -> throw new IllegalStateException("처리되지 않은 refresh 결과입니다.");
        };
    }

    void revoke(AuthApiModels.RevokeRequest request, String traceId) {
        transactions.executeWithoutResult(status -> {
            AuthModels.Session session = repository.findSessionForUpdate(request.authSessionId()).orElse(null);
            if (session == null || session.isRevoked()) {
                return;
            }
            Instant now = Instant.now(clock);
            String reason = session.isExpired(now) ? "EXPIRED" : "CURRENT_LOGOUT";
            if (repository.revokeSession(session, reason, now, denyUntil(now), traceId)) {
                repository.insertAudit(
                        session.userId(),
                        session.id(),
                        "SESSION_REVOKED",
                        reason,
                        now,
                        traceId,
                        Map.of()
                );
            }
        });
    }

    AuthApiModels.ReauthenticateResponse reauthenticate(
            AuthApiModels.ReauthenticateRequest request,
            String traceId
    ) {
        validateReauthenticationRequest(request);
        return switch (request.method()) {
            case "PASSWORD" -> reauthenticatePassword(request, traceId);
            case "GOOGLE" -> reauthenticateGoogle(request, traceId);
            default -> throw new IllegalStateException("검증되지 않은 재인증 방식입니다.");
        };
    }

    void revokeAll(AuthApiModels.RevokeAllRequest request, String traceId) {
        Instant now = Instant.now(clock);
        if (request.authenticatedAt().isBefore(now.minus(properties.recentAuthWindow()))
                || request.authenticatedAt().isAfter(now.plus(FUTURE_AUTHENTICATION_SKEW))) {
            throw AuthRuntimeException.unauthorized(
                    "RECENT_AUTH_REQUIRED",
                    "모든 기기 로그아웃에는 최근 인증이 필요합니다."
            );
        }
        transactions.executeWithoutResult(status -> {
            AuthModels.Session current = repository.findSessionForUpdate(
                    request.currentAuthSessionId()
            ).orElseThrow(() -> AuthRuntimeException.forbidden(
                    "AUTH_SESSION_SUBJECT_MISMATCH",
                    "인증 세션과 사용자가 일치하지 않습니다."
            ));
            if (!current.userId().equals(request.userId())) {
                throw AuthRuntimeException.forbidden(
                        "AUTH_SESSION_SUBJECT_MISMATCH",
                        "인증 세션과 사용자가 일치하지 않습니다."
                );
            }
            List<AuthModels.Session> activeSessions = repository.findActiveSessionsForUpdate(
                    request.userId(),
                    now
            );
            for (AuthModels.Session session : activeSessions) {
                if (repository.revokeSession(
                        session,
                        "ALL_DEVICE_LOGOUT",
                        now,
                        denyUntil(now),
                        traceId
                )) {
                    repository.insertAudit(
                            session.userId(),
                            session.id(),
                            "SESSION_REVOKED",
                            "ALL_DEVICE_LOGOUT",
                            now,
                            traceId,
                            Map.of("scope", "ALL_DEVICES")
                    );
                }
            }
        });
    }

    private AuthApiModels.ReauthenticateResponse reauthenticatePassword(
            AuthApiModels.ReauthenticateRequest request,
            String traceId
    ) {
        Instant authenticatedAt = transactions.execute(status -> {
            ReauthenticationContext context = requireReauthenticationContext(request);
            AuthModels.Identity identity = repository.findIdentityByUserAndProvider(
                    request.userId(),
                    "LOCAL"
            ).orElse(null);
            Instant now = Instant.now(clock);
            if (identity == null
                    || identity.passwordHash() == null
                    || !passwords.matches(request.password(), identity.passwordHash())) {
                recordReauthentication(
                        context,
                        "REAUTHENTICATION_FAILURE",
                        "INVALID_CREDENTIALS",
                        request.method(),
                        now,
                        traceId
                );
                return null;
            }
            recordReauthentication(
                    context,
                    "REAUTHENTICATION_SUCCESS",
                    null,
                    request.method(),
                    now,
                    traceId
            );
            return now;
        });
        if (authenticatedAt == null) {
            throw reauthenticationFailed();
        }
        return new AuthApiModels.ReauthenticateResponse(authenticatedAt);
    }

    private AuthApiModels.ReauthenticateResponse reauthenticateGoogle(
            AuthApiModels.ReauthenticateRequest request,
            String traceId
    ) {
        transactions.executeWithoutResult(status -> requireReauthenticationContext(request));

        AuthModels.GoogleUser googleUser;
        try {
            googleUser = googleVerifier.verify(request.credential());
            validateGoogleUser(googleUser);
        } catch (AuthRuntimeException exception) {
            auditRecorder.failure(
                    request.userId(),
                    "REAUTHENTICATION_FAILURE",
                    exception.code(),
                    traceId
            );
            if ("AUTH_PROVIDER_UNAVAILABLE".equals(exception.code())) {
                throw exception;
            }
            throw reauthenticationFailed();
        }

        Instant authenticatedAt = transactions.execute(status -> {
            ReauthenticationContext context = requireReauthenticationContext(request);
            AuthModels.Identity identity = repository.findIdentity(
                    "GOOGLE",
                    googleUser.providerUserId()
            ).orElse(null);
            Instant now = Instant.now(clock);
            if (identity == null || !identity.userId().equals(request.userId())) {
                recordReauthentication(
                        context,
                        "REAUTHENTICATION_FAILURE",
                        "INVALID_CREDENTIALS",
                        request.method(),
                        now,
                        traceId
                );
                return null;
            }
            recordReauthentication(
                    context,
                    "REAUTHENTICATION_SUCCESS",
                    null,
                    request.method(),
                    now,
                    traceId
            );
            return now;
        });
        if (authenticatedAt == null) {
            throw reauthenticationFailed();
        }
        return new AuthApiModels.ReauthenticateResponse(authenticatedAt);
    }

    private ReauthenticationContext requireReauthenticationContext(
            AuthApiModels.ReauthenticateRequest request
    ) {
        AuthModels.Session session = repository.findSessionForUpdate(
                request.currentAuthSessionId()
        ).orElseThrow(AuthRuntimeService::reauthenticationSubjectMismatch);
        if (!session.userId().equals(request.userId())) {
            throw reauthenticationSubjectMismatch();
        }
        Instant now = Instant.now(clock);
        if (session.isRevoked() || session.isExpired(now)) {
            throw AuthRuntimeException.unauthorized(
                    "AUTH_SESSION_REVOKED",
                    "인증 세션이 만료되었거나 폐기되었습니다."
            );
        }
        AuthModels.User user = repository.findUserById(request.userId()).orElse(null);
        if (user == null || !user.isActive()) {
            throw AuthRuntimeException.unauthorized(
                    "AUTH_SESSION_REVOKED",
                    "인증 세션이 만료되었거나 폐기되었습니다."
            );
        }
        return new ReauthenticationContext(user, session);
    }

    private void recordReauthentication(
            ReauthenticationContext context,
            String eventType,
            String reasonCode,
            String method,
            Instant occurredAt,
            String traceId
    ) {
        repository.insertAudit(
                context.user().id(),
                context.session().id(),
                eventType,
                reasonCode,
                occurredAt,
                traceId,
                Map.of("method", method)
        );
    }

    private void validateReauthenticationRequest(AuthApiModels.ReauthenticateRequest request) {
        boolean passwordPresent = request.password() != null && !request.password().isBlank();
        boolean credentialPresent = request.credential() != null && !request.credential().isBlank();
        boolean valid = switch (request.method()) {
            case "PASSWORD" -> passwordPresent && !credentialPresent;
            case "GOOGLE" -> credentialPresent && !passwordPresent;
            default -> false;
        };
        if (!valid) {
            throw AuthRuntimeException.badRequest(
                    "INVALID_REQUEST",
                    "재인증 요청값이 올바르지 않습니다."
            );
        }
    }

    private AuthApiModels.TokenResponse issueSession(
            AuthModels.User user,
            String deviceLabel,
            Instant now,
            String traceId,
            String auditEvent
    ) {
        UUID sessionId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        Instant expiresAt = now.plus(properties.refreshTtl());
        String refreshToken = refreshTokens.issue();
        repository.insertSessionAndCredential(
                sessionId,
                user.id(),
                familyId,
                credentialId,
                refreshTokens.hash(refreshToken),
                now,
                expiresAt,
                deviceLabel
        );
        List<AuthApiModels.AccessTokenView> accessTokens = issueAccessTokens(user, sessionId, now);
        repository.insertAudit(
                user.id(),
                sessionId,
                auditEvent,
                null,
                now,
                traceId,
                Map.of()
        );
        return tokenResponse(user, sessionId, refreshToken, expiresAt, now, accessTokens);
    }

    private List<AuthApiModels.AccessTokenView> issueAccessTokens(
            AuthModels.User user,
            UUID sessionId,
            Instant now
    ) {
        List<AccessTokenIssuer.IssuedAccessToken> issued = accessTokenIssuer.issue(
                user.id(),
                sessionId,
                now
        );
        if (issued == null || issued.size() != REQUIRED_AUDIENCES.size()) {
            throw invalidIssuerOutput();
        }
        Set<String> audiences = new HashSet<>();
        List<AuthApiModels.AccessTokenView> response = issued.stream().map(token -> {
            if (token == null
                    || !REQUIRED_AUDIENCES.contains(token.audience())
                    || token.token() == null
                    || token.token().isBlank()
                    || token.expiresIn() != 600
                    || !audiences.add(token.audience())) {
                throw invalidIssuerOutput();
            }
            return new AuthApiModels.AccessTokenView(
                    token.audience(),
                    token.token(),
                    token.expiresIn()
            );
        }).toList();
        if (!audiences.equals(REQUIRED_AUDIENCES)) {
            throw invalidIssuerOutput();
        }
        return response;
    }

    private AuthApiModels.TokenResponse tokenResponse(
            AuthModels.User user,
            UUID sessionId,
            String refreshToken,
            Instant expiresAt,
            Instant now,
            List<AuthApiModels.AccessTokenView> accessTokens
    ) {
        return new AuthApiModels.TokenResponse(
                accessTokens,
                refreshToken,
                "Bearer",
                Duration.between(now, expiresAt).toSeconds(),
                sessionId,
                new AuthApiModels.UserView(
                        user.id(),
                        user.email(),
                        user.displayName(),
                        user.pictureUrl(),
                        user.status()
                )
        );
    }

    private Instant denyUntil(Instant now) {
        return now.plus(properties.accessDenyWindow());
    }

    private static String deviceLabel(AuthApiModels.ClientContext context) {
        if (context == null || context.deviceLabel() == null || context.deviceLabel().isBlank()) {
            return null;
        }
        return context.deviceLabel().trim();
    }

    private static String boundedDisplayName(String displayName, String fallback) {
        String value = displayName == null || displayName.isBlank() ? fallback : displayName.trim();
        return value.length() <= 200 ? value : value.substring(0, 200);
    }

    private static void validateGoogleUser(AuthModels.GoogleUser user) {
        if (user.providerUserId() == null
                || user.providerUserId().isBlank()
                || user.providerUserId().length() > 320
                || user.email() == null
                || user.email().length() > 320
                || !user.email().contains("@")) {
            throw AuthRuntimeException.unauthorized(
                    "GOOGLE_CREDENTIAL_INVALID",
                    "Google credential이 올바르지 않습니다."
            );
        }
    }

    private static AuthRuntimeException emailAlreadyRegistered() {
        return AuthRuntimeException.conflict(
                "EMAIL_ALREADY_REGISTERED",
                "이미 가입된 이메일입니다."
        );
    }

    private static AuthRuntimeException invalidCredentials() {
        return AuthRuntimeException.unauthorized(
                "INVALID_CREDENTIALS",
                "이메일 또는 비밀번호가 올바르지 않습니다."
        );
    }

    private static AuthRuntimeException reauthenticationFailed() {
        return AuthRuntimeException.unauthorized(
                "REAUTHENTICATION_FAILED",
                "인증 정보를 확인해 주세요."
        );
    }

    private static AuthRuntimeException reauthenticationSubjectMismatch() {
        return AuthRuntimeException.forbidden(
                "AUTH_SESSION_SUBJECT_MISMATCH",
                "인증 세션과 사용자가 일치하지 않습니다."
        );
    }

    private static AuthRuntimeException invalidIssuerOutput() {
        return AuthRuntimeException.serviceUnavailable(
                "TOKEN_ISSUER_INVALID_OUTPUT",
                "access token 발급 결과가 계약과 일치하지 않습니다."
        );
    }

    private static String knownConstraint(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        if (message == null) {
            return "unknown";
        }
        for (String constraint : List.of(
                "auth_users_email_unique",
                "auth_identities_provider_user_unique",
                "auth_identities_user_provider_unique",
                "auth_identities_password_boundary_check",
                "auth_sessions_refresh_family_unique",
                "auth_refresh_credentials_token_hash_unique",
                "auth_refresh_credentials_one_active_leaf"
        )) {
            if (message.contains(constraint)) {
                return constraint;
            }
        }
        return "unknown";
    }

    private static String sqlState(DataIntegrityViolationException exception) {
        return exception.getMostSpecificCause() instanceof SQLException sqlException
                ? sqlException.getSQLState()
                : "unknown";
    }

    private enum RefreshResult {
        SUCCESS,
        INVALID,
        REVOKED,
        EXPIRED,
        REUSE
    }

    private record RefreshOutcome(RefreshResult result, AuthApiModels.TokenResponse response) {

        static RefreshOutcome success(AuthApiModels.TokenResponse response) {
            return new RefreshOutcome(RefreshResult.SUCCESS, response);
        }

        static RefreshOutcome error(RefreshResult result) {
            return new RefreshOutcome(result, null);
        }
    }

    private record ReauthenticationContext(
            AuthModels.User user,
            AuthModels.Session session
    ) {
    }
}
