package com.meetingmind.bff.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class BffRequestMetricsFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/v1/";
    private final BffRolloutMetrics metrics;

    public BffRequestMetricsFilter(BffRolloutMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(API_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        boolean failedWithException = false;
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            failedWithException = true;
            throw exception;
        } finally {
            String operation = operation(request.getRequestURI());
            String outcome = outcome(operation, response.getStatus(), failedWithException);
            metrics.recordBrowserRequest(operation, outcome, System.nanoTime() - startedAt);
        }
    }

    private String operation(String path) {
        return switch (path) {
            case "/api/v1/auth/csrf" -> "csrf";
            case "/api/v1/auth/signup" -> "signup";
            case "/api/v1/auth/login" -> "login";
            case "/api/v1/auth/google" -> "google";
            case "/api/v1/auth/session" -> "session";
            case "/api/v1/auth/logout" -> "logout";
            case "/api/v1/auth/reauthenticate" -> "reauthenticate";
            case "/api/v1/auth/logout-all" -> "logout_all";
            default -> "protected";
        };
    }

    private String outcome(String operation, int status, boolean failedWithException) {
        if (failedWithException || status >= 500) {
            return "server_error";
        }
        if (status < 400) {
            return "success";
        }
        if (status == 401 && "protected".equals(operation)) {
            return "unauthenticated";
        }
        if ("login".equals(operation)
                || "signup".equals(operation)
                || "google".equals(operation)
                || "reauthenticate".equals(operation)) {
            return "rejected";
        }
        return "client_error";
    }
}
