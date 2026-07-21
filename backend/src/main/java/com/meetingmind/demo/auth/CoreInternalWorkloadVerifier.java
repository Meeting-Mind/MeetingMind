package com.meetingmind.demo.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Verifies the Auth workload identity for a narrow Core reconciliation endpoint. */
@Component
class CoreInternalWorkloadVerifier {

    private static final String TEST_PRINCIPAL_HEADER = "X-MeetingMind-Test-Principal";

    private final String authPrincipal;
    private final boolean testHeaderAllowed;

    CoreInternalWorkloadVerifier(
            @Value("${meetingmind.core-auth.outbox.auth-principal:spiffe://meetingmind.internal/ns/meetingmind/sa/meetingmind-auth}") String authPrincipal,
            @Value("${meetingmind.core-auth.outbox.allow-test-header:false}") boolean allowTestHeader,
            Environment environment
    ) {
        this.authPrincipal = authPrincipal;
        this.testHeaderAllowed = allowTestHeader && environment.acceptsProfiles(Profiles.of("local", "test", "integration"));
    }

    void requireAuthWorkload(HttpServletRequest request) {
        String principal = certificatePrincipal(request).orElseGet(() ->
                testHeaderAllowed ? request.getHeader(TEST_PRINCIPAL_HEADER) : null);
        if (!authPrincipal.equals(principal)) {
            throw new AuthException(HttpStatus.FORBIDDEN, "WORKLOAD_FORBIDDEN", "허용되지 않은 workload입니다.");
        }
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
                    .filter(valueString -> valueString.startsWith("spiffe://"))
                    .findFirst();
        } catch (Exception exception) {
            return Optional.empty();
        }
    }
}
