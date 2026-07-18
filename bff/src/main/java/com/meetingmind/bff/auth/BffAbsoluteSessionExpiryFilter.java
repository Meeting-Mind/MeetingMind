package com.meetingmind.bff.auth;

import com.meetingmind.bff.tokenvault.TokenVault;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class BffAbsoluteSessionExpiryFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/v1/auth/csrf",
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/google",
            "/api/v1/auth/session",
            "/api/v1/auth/logout");

    private final TokenVault tokenVault;
    private final Clock clock;

    public BffAbsoluteSessionExpiryFilter(TokenVault tokenVault, Clock clock) {
        this.tokenVault = tokenVault;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Object absoluteExpiry = session.getAttribute(BffSessionAttributes.ABSOLUTE_EXPIRES_AT);
        boolean authenticatedSession = session.getAttribute(BffSessionAttributes.USER_ID) != null;
        if (!authenticatedSession) {
            filterChain.doFilter(request, response);
            return;
        }
        if (absoluteExpiry instanceof Instant expiresAt && expiresAt.isAfter(clock.instant())) {
            filterChain.doFilter(request, response);
            return;
        }

        Object tokenBundleId = session.getAttribute(BffSessionAttributes.TOKEN_BUNDLE_ID);
        if (tokenBundleId instanceof UUID bundleId) {
            try {
                tokenVault.delete(bundleId);
            } catch (RuntimeException ignored) {
                // Authentication is still invalidated; encrypted data remains bounded by TTL.
            }
        }
        try {
            session.invalidate();
        } catch (RuntimeException ignored) {
            // Clearing the security context keeps this request fail closed.
        }
        SecurityContextHolder.clearContext();

        if (PUBLIC_AUTH_PATHS.contains(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        response.sendError(HttpStatus.UNAUTHORIZED.value());
    }
}
