package com.meetingmind.bff.auth;

import com.meetingmind.bff.config.TokenManagerPolicy;
import com.meetingmind.bff.observability.BffRolloutMetrics;
import com.meetingmind.bff.tokenvault.TokenBundlePayload;
import com.meetingmind.bff.tokenvault.TokenVault;
import com.meetingmind.bff.tokenvault.VersionedTokenBundle;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class BffTokenManager {

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
        SessionReference session = requireSession(request);
        VersionedTokenBundle tokens = readOrInvalidate(session, request, null);
        boolean refreshed = false;

        if (!tokens.payload().accessExpiresAt().isAfter(clock.instant().plus(policy.accessExpirySkew()))) {
            tokens = refreshOrInvalidate(session, tokens, request);
            refreshed = true;
        }

        try {
            return downstreamCall.execute(authorization(tokens.payload()));
        } catch (DownstreamUnauthorizedException unauthorized) {
            if (refreshed) {
                throw invalidate(request, tokens.payload());
            }
        }

        VersionedTokenBundle refreshedTokens = refreshOrInvalidate(session, tokens, request);
        try {
            return downstreamCall.execute(authorization(refreshedTokens.payload()));
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
                    tokens.payload().tokenType(),
                    tokens.payload().accessToken(),
                    tokens.payload().refreshToken());
        } catch (RuntimeException ignored) {
            // Logout is idempotent and fails closed even when Auth or the vault is unavailable.
        } finally {
            sessionManager.invalidateCurrentSession(request);
        }
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
                                lockedCurrent.payload().refreshToken(), userAgent);
                        if (!session.userId().equals(response.user().id())) {
                            throw BffAuthException.of(
                                    HttpStatus.UNAUTHORIZED,
                                    "SESSION_INVALID",
                                    "로그인이 만료되었습니다. 다시 로그인해 주세요.");
                        }
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
        if (!(userId instanceof String user)
                || user.isBlank()
                || !(authSessionId instanceof UUID authId)
                || !(tokenBundleId instanceof UUID bundleId)
                || !(absoluteExpiresAt instanceof Instant absolute)
                || !absolute.isAfter(clock.instant())) {
            sessionManager.invalidateCurrentSession(request);
            throw invalidSession();
        }
        return new SessionReference(user, authId, bundleId, absolute);
    }

    private BffAuthException invalidate(HttpServletRequest request, TokenBundlePayload tokens) {
        if (tokens != null) {
            try {
                compatibilityAuthClient.revokeBestEffort(
                        tokens.tokenType(), tokens.accessToken(), tokens.refreshToken());
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

    private String authorization(TokenBundlePayload tokens) {
        return tokens.tokenType() + " " + tokens.accessToken();
    }

    private void waitForLeader() {
        try {
            TimeUnit.NANOSECONDS.sleep(policy.pollInterval().toNanos());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw invalidSession();
        }
    }

    private Instant minimum(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private record SessionReference(
            String userId, UUID authSessionId, UUID tokenBundleId, Instant absoluteExpiresAt) {}
}
