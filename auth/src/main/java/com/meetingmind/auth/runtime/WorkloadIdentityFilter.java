package com.meetingmind.auth.runtime;

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
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class WorkloadIdentityFilter extends OncePerRequestFilter {

    private static final String TEST_PRINCIPAL_HEADER = "X-MeetingMind-Test-Principal";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final AuthRuntimeProperties.Workload properties;
    private final boolean testHeaderAllowed;

    WorkloadIdentityFilter(AuthRuntimeProperties properties, Environment environment) {
        this.properties = properties.workload();
        this.testHeaderAllowed = this.properties.allowTestHeader()
                && environment.acceptsProfiles(Profiles.of("local", "test", "integration"));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/internal/v1/auth/") && !path.equals("/.well-known/jwks.json");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String principal = certificatePrincipal(request).orElseGet(() ->
                testHeaderAllowed ? request.getHeader(TEST_PRINCIPAL_HEADER) : null
        );
        if (principal == null || principal.isBlank()) {
            writeError(response, 401, "WORKLOAD_AUTH_REQUIRED", "workload 인증이 필요합니다.", request);
            return;
        }
        if (!allowedPrincipals(request).contains(principal)) {
            writeError(response, 403, "WORKLOAD_FORBIDDEN", "허용되지 않은 workload입니다.", request);
            return;
        }
        request.setAttribute(WorkloadIdentityFilter.class.getName() + ".principal", principal);
        filterChain.doFilter(request, response);
    }

    private java.util.Set<String> allowedPrincipals(HttpServletRequest request) {
        return request.getRequestURI().equals("/.well-known/jwks.json")
                ? properties.jwksPrincipals()
                : properties.allowedPrincipals();
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
            List<String> principals = names.stream()
                    .filter(name -> name.size() >= 2 && Integer.valueOf(6).equals(name.get(0)))
                    .map(name -> name.get(1))
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(valueString -> valueString.startsWith("spiffe://"))
                    .distinct()
                    .toList();
            return principals.size() == 1
                    ? Optional.of(principals.getFirst())
                    : Optional.empty();
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private void writeError(
            HttpServletResponse response,
            int status,
            String code,
            String message,
            HttpServletRequest request
    ) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        OBJECT_MAPPER.writeValue(
                response.getOutputStream(),
                new AuthApiModels.ErrorResponse(
                        code,
                        message,
                        List.of(),
                        RequestTraceFilter.current(request)
                )
        );
    }
}
