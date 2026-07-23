package com.meetingmind.stt.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
final class SttWorkloadIdentityFilter extends OncePerRequestFilter {

    private static final String TEST_PRINCIPAL_HEADER = "X-MeetingMind-Test-Principal";
    private final Set<String> allowedPrincipals;
    private final boolean testHeaderAllowed;
    private final ObjectMapper objectMapper;

    SttWorkloadIdentityFilter(
            @Value("${meetingmind.auth.workload.allowed-principals}") Set<String> allowedPrincipals,
            @Value("${meetingmind.auth.workload.allow-test-principal-header:false}")
                    boolean allowTestPrincipalHeader,
            Environment environment,
            ObjectMapper objectMapper) {
        this.allowedPrincipals = Set.copyOf(allowedPrincipals);
        this.testHeaderAllowed = allowTestPrincipalHeader
                && environment.acceptsProfiles(Profiles.of("local", "test", "integration"));
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String principal = certificatePrincipal(request).orElseGet(() ->
                testHeaderAllowed ? request.getHeader(TEST_PRINCIPAL_HEADER) : null);
        if (principal == null || principal.isBlank()) {
            writeError(response, 401, "WORKLOAD_AUTH_REQUIRED", "workload 인증이 필요합니다.");
            return;
        }
        if (!allowedPrincipals.contains(principal)) {
            writeError(response, 403, "WORKLOAD_FORBIDDEN", "허용되지 않은 workload입니다.");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Optional<String> certificatePrincipal(HttpServletRequest request) {
        Object value = request.getAttribute("jakarta.servlet.request.X509Certificate");
        if (!(value instanceof X509Certificate[] certificates) || certificates.length == 0) {
            value = request.getAttribute("javax.servlet.request.X509Certificate");
        }
        if (!(value instanceof X509Certificate[] certificates) || certificates.length == 0) {
            return Optional.empty();
        }
        try {
            Collection<List<?>> names = certificates[0].getSubjectAlternativeNames();
            if (names == null) {
                return Optional.empty();
            }
            return names.stream()
                    .filter(name -> name.size() >= 2 && Integer.valueOf(6).equals(name.get(0)))
                    .map(name -> name.get(1))
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(name -> name.startsWith("spiffe://"))
                    .findFirst();
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private void writeError(
            HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(
                response.getOutputStream(),
                new AuthErrorResponse(
                        code,
                        message,
                        List.of(),
                        com.meetingmind.stt.observability.RequestTrace.currentOrCreate()));
    }
}
