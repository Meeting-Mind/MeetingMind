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
    private final PasswordResetTokenSupport passwordResetTokens;
    private final PasswordResetDelivery passwordResetDelivery;
    private final ProfileImageStorage profileImageStorage;
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
            PasswordResetTokenSupport passwordResetTokens,
            PasswordResetDelivery passwordResetDelivery,
            ProfileImageStorage profileImageStorage,
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
        this.passwordResetTokens = passwordResetTokens;
        this.passwordResetDelivery = passwordResetDelivery;
        this.profileImageStorage = profileImageStorage;
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
                String passwordHash = passwords.hash(request.password());
                repository.insertIdentity(
                        UUID.randomUUID(),
                        user.id(),
                        "LOCAL",
                        email,
                        passwordHash,
                        now
                );
                repository.insertPasswordHistory(user.id(), passwordHash, now);
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

    void revokeAll(AuthApiModels.RevokeAllRequest request, String traceId) {
        Instant now = requireRecentAuthentication(request.authenticatedAt());
        transactions.executeWithoutResult(status -> {
            requireSessionSubject(request.currentAuthSessionId(), request.userId());
            revokeAllUserSessions(request.userId(), "ALL_DEVICE_LOGOUT", now, traceId);
        });
    }

    void reauthenticate(AuthApiModels.ReauthenticateRequest request, String traceId) {
        transactions.executeWithoutResult(status -> {
            Instant now = Instant.now(clock);
            AuthModels.Session session = requireCurrentSession(request.currentAuthSessionId(), request.userId(), now);
            AuthModels.User user = repository.findUserByIdForUpdate(request.userId()).orElseThrow(
                    AuthRuntimeService::invalidCredentials
            );
            if (!user.isActive()) {
                throw invalidCredentials();
            }
            if (hasText(request.password())) {
                AuthModels.Identity identity = repository.findIdentityForUserForUpdate(user.id(), "LOCAL")
                        .orElseThrow(AuthRuntimeService::invalidCredentials);
                if (!passwords.matches(request.password(), identity.passwordHash())) {
                    throw invalidCredentials();
                }
                repository.touchIdentityAndUser(identity, now);
            } else {
                AuthModels.GoogleUser googleUser = googleVerifier.verify(request.googleCredential());
                AuthModels.Identity identity = repository.findIdentityForUserForUpdate(user.id(), "GOOGLE")
                        .orElseThrow(AuthRuntimeService::invalidCredentials);
                if (!identity.providerUserId().equals(googleUser.providerUserId())) {
                    throw invalidCredentials();
                }
                repository.touchIdentityAndUser(identity, now);
            }
            repository.insertAudit(user.id(), session.id(), "REAUTHENTICATION_SUCCESS", null, now, traceId, Map.of());
        });
    }

    AuthApiModels.AcceptedResponse requestPasswordReset(
            AuthApiModels.PasswordResetRequest request,
            String traceId
    ) {
        if (!passwordResetDelivery.isAvailable()) {
            return new AuthApiModels.AcceptedResponse(true);
        }
        String email = JdbcAuthRepository.canonicalEmail(request.email());
        try {
            transactions.executeWithoutResult(status -> {
                AuthModels.User user = repository.findUserByEmail(email).orElse(null);
                if (user == null || !user.isActive()
                        || repository.findIdentityForUserForUpdate(user.id(), "LOCAL").isEmpty()) {
                    return;
                }
                Instant now = Instant.now(clock);
                Instant hourlyWindow = now.minus(Duration.ofHours(1));
                repository.lockPasswordResetRateLimits(user.id(), request.requestIpPrefix());
                if (repository.countPasswordResetRequestsForUser(user.id(), hourlyWindow) >= 3
                        || repository.countPasswordResetRequestsForIp(request.requestIpPrefix(), hourlyWindow) >= 10) {
                    return;
                }
                String token = passwordResetTokens.issue();
                Instant expiresAt = now.plus(Duration.ofMinutes(15));
                repository.insertPasswordResetToken(
                        UUID.randomUUID(),
                        user.id(),
                        passwordResetTokens.hash(token),
                        request.requestIpPrefix(),
                        now,
                        expiresAt
                );
                passwordResetDelivery.deliver(user, token, expiresAt);
                repository.insertAudit(user.id(), null, "PASSWORD_RESET_REQUESTED", null, now, traceId, Map.of());
            });
        } catch (RuntimeException exception) {
            LOGGER.warn("password_reset_request_not_delivered trace_id={}", traceId);
        }
        return new AuthApiModels.AcceptedResponse(true);
    }

    void resetPassword(AuthApiModels.PasswordResetConfirmRequest request, String traceId) {
        validateNewPassword(request.newPassword());
        transactions.executeWithoutResult(status -> {
            Instant now = Instant.now(clock);
            AuthModels.PasswordResetState state = repository.findPasswordResetStateForUpdate(
                    passwordResetTokens.hash(request.token())
            ).orElseThrow(AuthRuntimeService::invalidPasswordResetToken);
            if (!state.token().isUsable(now) || !state.user().isActive()) {
                throw invalidPasswordResetToken();
            }
            AuthModels.Identity localIdentity = repository.findIdentityForUserForUpdate(state.user().id(), "LOCAL")
                    .orElseThrow(AuthRuntimeService::invalidPasswordResetToken);
            rejectPasswordReuse(state.user().id(), request.newPassword());
            if (!repository.consumePasswordResetToken(state.token().id(), now)) {
                throw invalidPasswordResetToken();
            }
            String passwordHash = passwords.hash(request.newPassword());
            repository.updateLocalPassword(localIdentity.id(), passwordHash, now);
            repository.insertPasswordHistory(state.user().id(), passwordHash, now);
            revokeAllUserSessions(state.user().id(), "PASSWORD_RESET", now, traceId);
            repository.insertAudit(state.user().id(), null, "PASSWORD_RESET_SUCCESS", null, now, traceId, Map.of());
        });
    }

    void changePassword(AuthApiModels.PasswordChangeRequest request, String traceId) {
        validateNewPassword(request.newPassword());
        transactions.executeWithoutResult(status -> {
            Instant now = Instant.now(clock);
            requireCurrentSession(request.currentAuthSessionId(), request.userId(), now);
            AuthModels.User user = repository.findUserByIdForUpdate(request.userId()).orElseThrow(
                    AuthRuntimeService::invalidCredentials
            );
            AuthModels.Identity localIdentity = repository.findIdentityForUserForUpdate(user.id(), "LOCAL")
                    .orElseThrow(() -> AuthRuntimeException.conflict(
                            "LOCAL_CREDENTIAL_REQUIRED",
                            "local 비밀번호가 설정된 계정만 비밀번호를 변경할 수 있습니다."
                    ));
            if (!user.isActive() || !passwords.matches(request.currentPassword(), localIdentity.passwordHash())) {
                throw invalidCredentials();
            }
            rejectPasswordReuse(user.id(), request.newPassword());
            String passwordHash = passwords.hash(request.newPassword());
            repository.updateLocalPassword(localIdentity.id(), passwordHash, now);
            repository.insertPasswordHistory(user.id(), passwordHash, now);
            revokeAllUserSessions(user.id(), "PASSWORD_CHANGED", now, traceId);
            repository.insertAudit(user.id(), null, "PASSWORD_CHANGED", null, now, traceId, Map.of());
        });
    }

    AuthApiModels.UserView updateProfile(AuthApiModels.ProfileUpdateRequest request, String traceId) {
        return transactions.execute(status -> {
            Instant now = Instant.now(clock);
            AuthModels.User existing = repository.findUserByIdForUpdate(request.userId()).orElseThrow(
                    AuthRuntimeService::invalidCredentials
            );
            if (!existing.isActive()) {
                throw invalidCredentials();
            }
            AuthModels.User user = repository.updateProfile(request.userId(), request.displayName(), now);
            repository.insertAudit(user.id(), null, "PROFILE_UPDATED", null, now, traceId, Map.of());
            return userView(user);
        });
    }

    AuthApiModels.UserView updateProfileImage(
            UUID userId,
            String declaredContentType,
            byte[] bytes,
            String traceId) {
        ProfileImageValidator.ValidatedImage image = ProfileImageValidator.validate(declaredContentType, bytes);
        if (!profileImageStorage.isAvailable()) {
            throw AuthRuntimeException.serviceUnavailable(
                    "PROFILE_IMAGE_STORAGE_UNAVAILABLE",
                    "프로필 사진 저장소를 사용할 수 없습니다.");
        }
        AuthModels.User existing = repository.findUserById(userId).orElseThrow(AuthRuntimeService::invalidCredentials);
        if (!existing.isActive()) {
            throw invalidCredentials();
        }
        String newKey = profileImageStorage.store(userId, image);
        AuthModels.User updated;
        try {
            updated = transactions.execute(status -> {
                AuthModels.User current = repository.findUserByIdForUpdate(userId).orElseThrow(
                        AuthRuntimeService::invalidCredentials);
                if (!current.isActive()) {
                    throw invalidCredentials();
                }
                Instant now = Instant.now(clock);
                AuthModels.User user = repository.updateProfileImage(userId, newKey, now);
                repository.insertAudit(user.id(), null, "PROFILE_IMAGE_UPDATED", null, now, traceId, Map.of());
                return user;
            });
        } catch (RuntimeException exception) {
            deleteBestEffort(newKey);
            throw exception;
        }
        if (profileImageStorage.isManagedKey(existing.pictureUrl())) {
            deleteBestEffort(existing.pictureUrl());
        }
        return userView(updated);
    }

    void withdraw(AuthApiModels.WithdrawalRequest request, String traceId) {
        Instant now = requireRecentAuthentication(request.authenticatedAt());
        String deletedProfileImage = transactions.execute(status -> {
            requireCurrentSession(request.currentAuthSessionId(), request.userId(), now);
            AuthModels.User user = repository.findUserByIdForUpdate(request.userId())
                    .orElseThrow(AuthRuntimeService::invalidCredentials);
            if (repository.disableUser(request.userId(), now)) {
                revokeAllUserSessions(request.userId(), "ACCOUNT_WITHDRAWAL", now, traceId);
                repository.insertAudit(request.userId(), null, "ACCOUNT_WITHDRAWAL", null, now, traceId, Map.of());
                return user.pictureUrl();
            }
            return null;
        });
        if (profileImageStorage.isManagedKey(deletedProfileImage)) {
            deleteBestEffort(deletedProfileImage);
        }
    }

    private void deleteBestEffort(String objectKey) {
        try {
            profileImageStorage.delete(objectKey);
        } catch (RuntimeException exception) {
            LOGGER.warn("profile_image_delete_failed");
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

    private AuthModels.Session requireCurrentSession(UUID authSessionId, UUID userId, Instant now) {
        AuthModels.Session session = requireSessionSubject(authSessionId, userId);
        if (session.isRevoked() || session.isExpired(now)) {
            throw subjectMismatch();
        }
        return session;
    }

    private AuthModels.Session requireSessionSubject(UUID authSessionId, UUID userId) {
        AuthModels.Session session = repository.findSessionForUpdate(authSessionId).orElseThrow(
                AuthRuntimeService::subjectMismatch
        );
        if (!session.userId().equals(userId)) {
            throw subjectMismatch();
        }
        return session;
    }

    private void revokeAllUserSessions(UUID userId, String reason, Instant now, String traceId) {
        for (AuthModels.Session session : repository.findActiveSessionsForUpdate(userId, now)) {
            if (repository.revokeSession(session, reason, now, denyUntil(now), traceId)) {
                repository.insertAudit(
                        session.userId(),
                        session.id(),
                        "SESSION_REVOKED",
                        reason,
                        now,
                        traceId,
                        Map.of("scope", "ALL_DEVICES")
                );
            }
        }
    }

    private Instant requireRecentAuthentication(Instant authenticatedAt) {
        Instant now = Instant.now(clock);
        if (authenticatedAt.isBefore(now.minus(properties.recentAuthWindow()))
                || authenticatedAt.isAfter(now.plus(FUTURE_AUTHENTICATION_SKEW))) {
            throw AuthRuntimeException.unauthorized(
                    "RECENT_AUTH_REQUIRED",
                    "최근 인증이 필요합니다."
            );
        }
        return now;
    }

    private void validateNewPassword(String password) {
        if (!passwords.isValid(password)) {
            throw AuthRuntimeException.badRequest("INVALID_REQUEST", PasswordSupport.POLICY_MESSAGE);
        }
    }

    private void rejectPasswordReuse(UUID userId, String candidate) {
        if (repository.findRecentPasswordHashes(userId, 3).stream().anyMatch(hash -> passwords.matches(candidate, hash))) {
            throw AuthRuntimeException.badRequest(
                    "PASSWORD_REUSE_FORBIDDEN",
                    "최근 3개의 비밀번호는 다시 사용할 수 없습니다."
            );
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static AuthApiModels.UserView userView(AuthModels.User user) {
        return new AuthApiModels.UserView(
                user.id(), user.email(), user.displayName(), user.pictureUrl(), user.status());
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

    private static AuthRuntimeException invalidPasswordResetToken() {
        return AuthRuntimeException.badRequest(
                "PASSWORD_RESET_TOKEN_INVALID",
                "비밀번호 재설정 링크가 유효하지 않습니다."
        );
    }

    private static AuthRuntimeException subjectMismatch() {
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
}
