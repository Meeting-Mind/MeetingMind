package com.meetingmind.bff.auth;

import com.meetingmind.bff.config.TokenManagerPolicy;
import com.meetingmind.bff.observability.BffRolloutMetrics;
import com.meetingmind.bff.proxy.DownstreamService;
import com.meetingmind.bff.tokenvault.TokenBundlePayload;
import com.meetingmind.bff.tokenvault.TokenVault;
import com.meetingmind.bff.tokenvault.VersionedTokenBundle;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class BffTokenManager {

    private static final Duration RECENT_AUTH_WINDOW = Duration.ofMinutes(10);

    private final TokenVault tokenVault;
    private final CompatibilityAuthClient compatibilityAuthClient;
    private final RefreshSingleFlightLock refreshLock;
    private final BffSessionManager sessionManager;
    private final TokenManagerPolicy policy;
    private final Clock clock;
    private final BffRolloutMetrics rolloutMetrics;

    public BffTokenManager(
            TokenVault tokenVault,
            CompatibilityAuthClient compatibilityAuthClient,
            RefreshSingleFlightLock refreshLock,
            BffSessionManager sessionManager,
            TokenManagerPolicy policy,
            Clock clock,
            BffRolloutMetrics rolloutMetrics) {
        this.tokenVault = tokenVault;
        this.compatibilityAuthClient = compatibilityAuthClient;
        this.refreshLock = refreshLock;
        this.sessionManager = sessionManager;
        this.policy = policy;
        this.clock = clock;
        this.rolloutMetrics = rolloutMetrics;
    }

    public <T> T execute(HttpServletRequest request, AuthorizedDownstreamCall<T> downstreamCall) {
        return execute(request, DownstreamService.CORE, downstreamCall);
    }

    public <T> T execute(
            HttpServletRequest request,
            DownstreamService downstreamService,
            AuthorizedDownstreamCall<T> downstreamCall) {
        SessionReference session = requireSession(request);
        VersionedTokenBundle tokens = readOrInvalidate(session, request, null);
        boolean refreshed = false;

        if (!accessToken(tokens.payload(), downstreamService).expiresAt()
                .isAfter(clock.instant().plus(policy.accessExpirySkew()))) {
            tokens = refreshOrInvalidate(session, tokens, request);
            refreshed = true;
        }

        try {
            return downstreamCall.execute(authorization(tokens.payload(), downstreamService));
        } catch (DownstreamUnauthorizedException unauthorized) {
            if (refreshed) {
                throw invalidate(request, tokens.payload());
            }
        }

        VersionedTokenBundle refreshedTokens = refreshOrInvalidate(session, tokens, request);
        try {
            return downstreamCall.execute(authorization(refreshedTokens.payload(), downstreamService));
        } catch (DownstreamUnauthorizedException unauthorized) {
            throw invalidate(request, refreshedTokens.payload());
        }
    }

    public void logout(HttpServletRequest request) {
        try {
            SessionReference session = requireSession(request);
            VersionedTokenBundle tokens = tokenVault.readVersioned(
                    session.tokenBundleId(), session.authSessionId());
            if (!tokens.payload().accessExpiresAt().isAfter(clock.instant().plus(policy.accessExpirySkew()))) {
                tokens = refreshSingleFlight(session, tokens, request.getHeader(HttpHeaders.USER_AGENT));
            }
            compatibilityAuthClient.revokeBestEffort(
                    session.authSessionId(),
                    tokens.payload().tokenType(),
                    tokens.payload().accessToken(),
                    tokens.payload().refreshToken());
        } catch (RuntimeException ignored) {
            // Logout is idempotent and fails closed even when Auth or the vault is unavailable.
        } finally {
            sessionManager.invalidateCurrentSession(request);
        }
    }

    public void logoutAll(HttpServletRequest request, BrowserAuthRequests.LogoutAll credentials) {
        SessionReference session = requireSession(request);
        UUID userId = parseAuthUserId(session.userId());
        Instant authenticatedAt = session.authenticatedAt();
        Instant now = clock.instant();
        if (!authenticatedAt.isAfter(now.minus(RECENT_AUTH_WINDOW))) {
            if (credentials == null || !hasExactlyOneCredential(credentials)) {
                throw BffAuthException.of(
                        HttpStatus.FORBIDDEN,
                        "REAUTHENTICATION_REQUIRED",
                        "모든 기기 로그아웃에는 최근 인증이 필요합니다.");
            }
            compatibilityAuthClient.reauthenticate(
                    session.authSessionId(), userId, credentials.password(), credentials.googleCredential());
            authenticatedAt = now;
        }
        try {
            compatibilityAuthClient.revokeAll(session.authSessionId(), userId, authenticatedAt);
        } finally {
            sessionManager.invalidateUserSessions(session.userId());
            sessionManager.invalidateCurrentSession(request);
        }
    }

    public boolean requestPasswordReset(BrowserAuthRequests.PasswordResetRequest request, String requestIpPrefix) {
        return compatibilityAuthClient.requestPasswordReset(request.email(), requestIpPrefix);
    }

    public void resetPassword(BrowserAuthRequests.PasswordResetConfirm request) {
        compatibilityAuthClient.resetPassword(request.token(), request.newPassword());
    }

    public void changePassword(HttpServletRequest request, BrowserAuthRequests.PasswordChange change) {
        SessionReference session = requireSession(request);
        UUID userId = parseTargetAuthUserId(session.userId());
        try {
            compatibilityAuthClient.changePassword(
                    session.authSessionId(), userId, change.currentPassword(), change.newPassword());
            sessionManager.invalidateUserSessions(session.userId());
            sessionManager.invalidateCurrentSession(request);
        } catch (BffAuthException exception) {
            if ("SESSION_INVALID".equals(exception.code())) {
                sessionManager.invalidateCurrentSession(request);
            }
            throw exception;
        }
    }

    public BffAuthUser updateProfile(HttpServletRequest request, BrowserAuthRequests.ProfileUpdate update) {
        SessionReference session = requireSession(request);
        BffAuthUser user = compatibilityAuthClient.updateProfile(parseTargetAuthUserId(session.userId()), update.displayName());
        return synchronizeProfile(session, request, user);
    }

    public BffAuthUser updateProfileImage(
            HttpServletRequest request,
            ProfileImageUploadValidator.ValidatedUpload upload) {
        SessionReference session = requireSession(request);
        BffAuthUser user = compatibilityAuthClient.updateProfileImage(
                parseTargetAuthUserId(session.userId()), upload.contentType(), upload.filename(), upload.bytes());
        return synchronizeProfile(session, request, user);
    }

    public void withdraw(HttpServletRequest request, BrowserAuthRequests.Withdrawal withdrawal) {
        SessionReference session = requireSession(request);
        UUID userId = parseTargetAuthUserId(session.userId());
        Instant now = clock.instant();
        Instant authenticatedAt = session.authenticatedAt();
        if (!authenticatedAt.isAfter(now.minus(RECENT_AUTH_WINDOW))) {
            if (!hasExactlyOneCredential(withdrawal.password(), withdrawal.googleCredential())) {
                throw BffAuthException.of(
                        HttpStatus.FORBIDDEN,
                        "REAUTHENTICATION_REQUIRED",
                        "계정 탈퇴에는 최근 인증이 필요합니다.");
            }
            compatibilityAuthClient.reauthenticate(
                    session.authSessionId(), userId, withdrawal.password(), withdrawal.googleCredential());
            authenticatedAt = now;
        }
        VersionedTokenBundle tokens = readOrInvalidate(session, request, null);
        if (!accessToken(tokens.payload(), DownstreamService.CORE).expiresAt()
                .isAfter(now.plus(policy.accessExpirySkew()))) {
            tokens = refreshOrInvalidate(session, tokens, request);
        }
        String tokenType = tokens.payload().tokenType();
        String coreAccessToken = accessToken(tokens.payload(), DownstreamService.CORE).token();
        compatibilityAuthClient.prepareWithdrawal(tokenType, coreAccessToken);
        try {
            compatibilityAuthClient.withdraw(session.authSessionId(), userId, authenticatedAt);
        } catch (RuntimeException exception) {
            cancelWithdrawalBestEffort(tokenType, coreAccessToken);
            throw exception;
        }
        try {
            compatibilityAuthClient.completeWithdrawal(tokenType, coreAccessToken);
        } finally {
            sessionManager.invalidateUserSessions(session.userId());
            sessionManager.invalidateCurrentSession(request);
        }
    }

    private BffAuthUser synchronizeProfile(
            SessionReference session,
            HttpServletRequest request,
            BffAuthUser user) {
        if (!session.userId().equals(user.id())) {
            throw invalidate(request, null);
        }
        VersionedTokenBundle tokens = readOrInvalidate(session, request, null);
        if (!accessToken(tokens.payload(), DownstreamService.CORE).expiresAt()
                .isAfter(clock.instant().plus(policy.accessExpirySkew()))) {
            tokens = refreshOrInvalidate(session, tokens, request);
        }
        compatibilityAuthClient.projectProfile(
                user,
                tokens.payload().tokenType(),
                accessToken(tokens.payload(), DownstreamService.CORE).token());
        return user;
    }

    private VersionedTokenBundle refreshOrInvalidate(
            SessionReference session, VersionedTokenBundle observed, HttpServletRequest request) {
        try {
            return refreshSingleFlight(session, observed, request.getHeader(HttpHeaders.USER_AGENT));
        } catch (RuntimeException exception) {
            throw invalidate(request, observed.payload());
        }
    }

    private VersionedTokenBundle refreshSingleFlight(
            SessionReference session, VersionedTokenBundle observed, String userAgent) {
        String lockOwner = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + policy.waitTimeout().toNanos();

        while (true) {
            VersionedTokenBundle current = tokenVault.readVersioned(session.tokenBundleId(), session.authSessionId());
            if (current.version() != observed.version()) {
                return current;
            }

            if (refreshLock.tryAcquire(session.tokenBundleId(), lockOwner, policy.lockLease())) {
                try {
                    try {
                        VersionedTokenBundle lockedCurrent =
                                tokenVault.readVersioned(session.tokenBundleId(), session.authSessionId());
                        if (lockedCurrent.version() != observed.version()) {
                            return lockedCurrent;
                        }
                        LegacyAuthTokenResponse response = compatibilityAuthClient.refresh(
                                session.authSessionId(), lockedCurrent.payload().refreshToken(), userAgent);
                        if (!session.userId().equals(response.user().id())) {
                            throw BffAuthException.of(
                                    HttpStatus.UNAUTHORIZED,
                                    "SESSION_INVALID",
                                    "로그인이 만료되었습니다. 다시 로그인해 주세요.");
                        }
                        compatibilityAuthClient.projectUser(response);
                        TokenBundlePayload replacement = replacement(session, lockedCurrent.payload(), response);
                        long version = tokenVault
                                .rotate(session.tokenBundleId(), lockedCurrent.version(), replacement)
                                .version();
                        rolloutMetrics.recordRefresh("success");
                        return new VersionedTokenBundle(version, replacement);
                    } catch (RuntimeException exception) {
                        rolloutMetrics.recordRefresh("failure");
                        throw exception;
                    }
                } finally {
                    refreshLock.release(session.tokenBundleId(), lockOwner);
                }
            }

            if (System.nanoTime() - deadline >= 0) {
                throw BffAuthException.of(
                        HttpStatus.UNAUTHORIZED,
                        "SESSION_INVALID",
                        "로그인이 만료되었습니다. 다시 로그인해 주세요.");
            }
            waitForLeader();
        }
    }

    private TokenBundlePayload replacement(
            SessionReference session,
            TokenBundlePayload current,
            LegacyAuthTokenResponse response) {
        Instant now = clock.instant();
        Instant refreshExpiresAt = minimum(
                now.plusSeconds(response.refreshExpiresIn()), session.absoluteExpiresAt());
        Instant accessExpiresAt = minimum(now.plusSeconds(response.expiresIn()), refreshExpiresAt);
        return new TokenBundlePayload(
                session.authSessionId(),
                response.accessToken(),
                response.refreshToken(),
                response.tokenType(),
                accessExpiresAt,
                refreshExpiresAt,
                current.issuer(),
                current.audiences(),
                current.scopes());
    }

    private VersionedTokenBundle readOrInvalidate(
            SessionReference session, HttpServletRequest request, TokenBundlePayload knownTokens) {
        try {
            return tokenVault.readVersioned(session.tokenBundleId(), session.authSessionId());
        } catch (RuntimeException exception) {
            throw invalidate(request, knownTokens);
        }
    }

    private SessionReference requireSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw invalidSession();
        }
        Object userId = session.getAttribute(BffSessionAttributes.USER_ID);
        Object authSessionId = session.getAttribute(BffSessionAttributes.AUTH_SESSION_ID);
        Object tokenBundleId = session.getAttribute(BffSessionAttributes.TOKEN_BUNDLE_ID);
        Object absoluteExpiresAt = session.getAttribute(BffSessionAttributes.ABSOLUTE_EXPIRES_AT);
        Object authenticatedAt = session.getAttribute(BffSessionAttributes.AUTHENTICATED_AT);
        if (!(userId instanceof String user)
                || user.isBlank()
                || !(authSessionId instanceof UUID authId)
                || !(tokenBundleId instanceof UUID bundleId)
                || !(absoluteExpiresAt instanceof Instant absolute)
                || !(authenticatedAt instanceof Instant authenticated)
                || !absolute.isAfter(clock.instant())) {
            sessionManager.invalidateCurrentSession(request);
            throw invalidSession();
        }
        return new SessionReference(user, authId, bundleId, absolute, authenticated);
    }

    private BffAuthException invalidate(HttpServletRequest request, TokenBundlePayload tokens) {
        if (tokens != null) {
            try {
                compatibilityAuthClient.revokeBestEffort(
                        tokens.authSessionId(), tokens.tokenType(), tokens.accessToken(), tokens.refreshToken());
            } catch (RuntimeException ignored) {
                // The browser session still fails closed; compatibility revoke failure is audited by the client.
            }
        }
        sessionManager.invalidateCurrentSession(request);
        return invalidSession();
    }

    private BffAuthException invalidSession() {
        return BffAuthException.of(
                HttpStatus.UNAUTHORIZED,
                "SESSION_INVALID",
                "로그인이 만료되었습니다. 다시 로그인해 주세요.");
    }

    private TokenBundlePayload.AccessToken accessToken(
            TokenBundlePayload tokens,
            DownstreamService downstreamService) {
        return tokens.accessTokenFor(downstreamService.audience());
    }

    private String authorization(TokenBundlePayload tokens, DownstreamService downstreamService) {
        return tokens.tokenType() + " " + accessToken(tokens, downstreamService).token();
    }

    private void waitForLeader() {
        try {
            TimeUnit.NANOSECONDS.sleep(policy.pollInterval().toNanos());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw invalidSession();
        }
    }

    private void cancelWithdrawalBestEffort(String tokenType, String coreAccessToken) {
        try {
            compatibilityAuthClient.cancelWithdrawal(tokenType, coreAccessToken);
        } catch (RuntimeException ignored) {
            // PREPARED reservations expire without triggering anonymization when Auth disable fails.
        }
    }

    private Instant minimum(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private static boolean hasExactlyOneCredential(String password, String googleCredential) {
        boolean passwordProvided = password != null && !password.isBlank();
        boolean googleCredentialProvided = googleCredential != null && !googleCredential.isBlank();
        return passwordProvided != googleCredentialProvided;
    }

    private UUID parseTargetAuthUserId(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (RuntimeException exception) {
            throw BffAuthException.of(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AUTH_ACCOUNT_UNAVAILABLE",
                    "현재 인증 전환에서는 계정 관리 기능을 사용할 수 없습니다.");
        }
    }

    private UUID parseAuthUserId(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (RuntimeException exception) {
            throw BffAuthException.of(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "REAUTHENTICATION_UNAVAILABLE",
                    "현재 인증 전환에서는 모든 기기 로그아웃을 사용할 수 없습니다.");
        }
    }

    private boolean hasExactlyOneCredential(BrowserAuthRequests.LogoutAll request) {
        return hasText(request.password()) ^ hasText(request.googleCredential());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record SessionReference(
            String userId, UUID authSessionId, UUID tokenBundleId, Instant absoluteExpiresAt, Instant authenticatedAt) {}
}
