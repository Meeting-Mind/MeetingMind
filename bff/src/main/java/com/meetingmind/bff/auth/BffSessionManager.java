package com.meetingmind.bff.auth;

import com.meetingmind.bff.config.BffSessionLifetimePolicy;
import com.meetingmind.bff.config.SessionCookieConfiguration;
import com.meetingmind.bff.tokenvault.TokenBundlePayload;
import com.meetingmind.bff.tokenvault.AudienceAccessToken;
import com.meetingmind.bff.tokenvault.TokenVault;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Service;

@Service
public class BffSessionManager {

    private final TokenVault tokenVault;
    private final AuthClient authClient;
    private final CoreUserProjectionClient coreUserProjectionClient;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final BffSessionLifetimePolicy lifetimePolicy;
    private final Clock clock;
    private final String issuer;

    public BffSessionManager(
            TokenVault tokenVault,
            AuthClient authClient,
            CoreUserProjectionClient coreUserProjectionClient,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository,
            BffSessionLifetimePolicy lifetimePolicy,
            Clock clock,
            @Value("${meetingmind.bff.auth-issuer}") String issuer) {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("BFF auth issuer is required");
        }
        this.tokenVault = tokenVault;
        this.authClient = authClient;
        this.coreUserProjectionClient = coreUserProjectionClient;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
        this.lifetimePolicy = lifetimePolicy;
        this.clock = clock;
        this.issuer = issuer;
    }

    public BffAuthenticatedResponse establish(
            AuthTokenResponse tokens,
            boolean rememberMe,
            HttpServletRequest request,
            HttpServletResponse response) {
        Instant now = clock.instant();
        Duration idleLifetime = rememberMe ? lifetimePolicy.rememberIdle() : lifetimePolicy.standardIdle();
        Duration absoluteLifetime =
                rememberMe ? lifetimePolicy.rememberAbsolute() : lifetimePolicy.standardAbsolute();
        Instant absoluteExpiresAt = now.plus(absoluteLifetime);
        UUID authSessionId = tokens.authSessionId();
        UUID tokenBundleId = UUID.randomUUID();
        boolean bundleCreated = false;

        try {
            coreUserProjectionClient.project(tokens);
        } catch (RuntimeException exception) {
            authClient.revokeBestEffort(tokens);
            throw exception;
        }

        try {
            Instant backendRefreshExpiresAt = now.plusSeconds(tokens.refreshExpiresIn());
            Instant vaultExpiresAt = backendRefreshExpiresAt.isBefore(absoluteExpiresAt)
                    ? backendRefreshExpiresAt
                    : absoluteExpiresAt;
            TokenBundlePayload payload = new TokenBundlePayload(
                    authSessionId,
                    tokens.schemaVersion(),
                    accessTokens(tokens, now, vaultExpiresAt),
                    tokens.refreshToken(),
                    tokens.tokenType(),
                    vaultExpiresAt,
                    issuer,
                    Map.of());
            tokenVault.create(tokenBundleId, payload);
            bundleCreated = true;

            BffAuthUser user = BffAuthUser.from(tokens.user());
            Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                    user, null, List.of());
            sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

            HttpSession session = request.getSession(true);
            session.setMaxInactiveInterval(Math.toIntExact(idleLifetime.toSeconds()));
            session.setAttribute(BffSessionAttributes.RESOURCE_USER_ID, user.id());
            session.setAttribute(BffSessionAttributes.AUTH_USER_ID, tokens.user().authUserId());
            session.setAttribute(
                    FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                    tokens.user().authUserId().toString());
            session.setAttribute(BffSessionAttributes.AUTH_SESSION_ID, authSessionId);
            session.setAttribute(BffSessionAttributes.TOKEN_BUNDLE_ID, tokenBundleId);
            session.setAttribute(BffSessionAttributes.CREATED_AT, now);
            session.setAttribute(BffSessionAttributes.ABSOLUTE_EXPIRES_AT, absoluteExpiresAt);
            session.setAttribute(BffSessionAttributes.REMEMBER_ME, rememberMe);
            session.setAttribute(BffSessionAttributes.AUTHENTICATED_AT, now);
            if (rememberMe) {
                request.setAttribute(
                        SessionCookieConfiguration.COOKIE_MAX_AGE_REQUEST_ATTRIBUTE,
                        Math.toIntExact(absoluteLifetime.toSeconds()));
            }

            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);
            securityContextRepository.saveContext(securityContext, request, response);

            return new BffAuthenticatedResponse(
                    user,
                    new BffSessionView(
                            absoluteExpiresAt,
                            minimum(now.plus(idleLifetime), absoluteExpiresAt),
                            rememberMe));
        } catch (RuntimeException exception) {
            cleanupFailedEstablishment(tokenBundleId, bundleCreated, tokens, request);
            throw BffAuthException.of(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AUTH_SESSION_UNAVAILABLE",
                    "로그인 세션을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    public BffSessionBootstrapResponse currentSession(
            Authentication authentication, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null
                || authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof BffAuthUser user)) {
            return BffSessionBootstrapResponse.unauthenticated();
        }

        Object userId = session.getAttribute(BffSessionAttributes.RESOURCE_USER_ID);
        Object authUserId = session.getAttribute(BffSessionAttributes.AUTH_USER_ID);
        Object authSessionId = session.getAttribute(BffSessionAttributes.AUTH_SESSION_ID);
        Object tokenBundleId = session.getAttribute(BffSessionAttributes.TOKEN_BUNDLE_ID);
        Object absoluteExpiry = session.getAttribute(BffSessionAttributes.ABSOLUTE_EXPIRES_AT);
        Object rememberMe = session.getAttribute(BffSessionAttributes.REMEMBER_ME);
        if (!user.id().equals(userId)
                || !(authUserId instanceof UUID)
                || !(authSessionId instanceof UUID)
                || !(tokenBundleId instanceof UUID)
                || !(absoluteExpiry instanceof Instant absoluteExpiresAt)
                || !(rememberMe instanceof Boolean remember)) {
            invalidate(session, tokenBundleId);
            return BffSessionBootstrapResponse.unauthenticated();
        }

        Instant now = clock.instant();
        if (!absoluteExpiresAt.isAfter(now)) {
            invalidate(session, tokenBundleId);
            return BffSessionBootstrapResponse.unauthenticated();
        }
        Instant idleExpiresAt = minimum(
                now.plusSeconds(session.getMaxInactiveInterval()), absoluteExpiresAt);
        return BffSessionBootstrapResponse.authenticated(
                user, new BffSessionView(absoluteExpiresAt, idleExpiresAt, remember));
    }

    public void invalidateCurrentSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            SecurityContextHolder.clearContext();
            return;
        }
        invalidate(session, session.getAttribute(BffSessionAttributes.TOKEN_BUNDLE_ID));
    }

    private void cleanupFailedEstablishment(
            UUID tokenBundleId,
            boolean bundleCreated,
            AuthTokenResponse tokens,
            HttpServletRequest request) {
        if (bundleCreated) {
            try {
                tokenVault.delete(tokenBundleId);
            } catch (RuntimeException ignored) {
                // Ciphertext expires no later than the attempted BFF session absolute expiry.
            }
        }
        authClient.revokeBestEffort(tokens);
        HttpSession session = request.getSession(false);
        if (session != null) {
            try {
                session.invalidate();
            } catch (RuntimeException ignored) {
                // Authentication is not saved; fail closed even if the store is unavailable.
            }
        }
        SecurityContextHolder.clearContext();
    }

    private void invalidate(HttpSession session, Object tokenBundleId) {
        if (tokenBundleId instanceof UUID bundleId) {
            try {
                tokenVault.delete(bundleId);
            } catch (RuntimeException ignored) {
                // Local authentication is still invalidated; encrypted data expires by TTL.
            }
        }
        try {
            session.invalidate();
        } catch (RuntimeException ignored) {
            // The request remains unauthenticated.
        }
        SecurityContextHolder.clearContext();
    }

    private Instant minimum(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private Map<String, AudienceAccessToken> accessTokens(
            AuthTokenResponse tokens, Instant now, Instant refreshExpiresAt) {
        return tokens.accessTokens().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> new AudienceAccessToken(
                                entry.getValue().token(),
                                minimum(
                                        now.plusSeconds(entry.getValue().expiresIn()),
                                        refreshExpiresAt))));
    }
}
