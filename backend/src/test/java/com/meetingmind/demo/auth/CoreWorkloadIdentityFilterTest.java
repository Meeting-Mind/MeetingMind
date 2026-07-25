package com.meetingmind.demo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CoreWorkloadIdentityFilterTest {

    private static final String BFF_PRINCIPAL =
            "spiffe://meetingmind.internal/ns/meetingmind/sa/meetingmind-bff";

    @Test
    void localTestHeaderRequiresExplicitProfileConfigurationAndAllowlist() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        CoreWorkloadIdentityFilter filter = new CoreWorkloadIdentityFilter(
                Set.of(BFF_PRINCIPAL), true, environment, new ObjectMapper());

        MockHttpServletRequest missing = request();
        MockHttpServletResponse missingResponse = new MockHttpServletResponse();
        filter.doFilter(missing, missingResponse, new MockFilterChain());
        assertThat(missingResponse.getStatus()).isEqualTo(401);

        MockHttpServletRequest wrong = request();
        wrong.addHeader("X-MeetingMind-Test-Principal", "spiffe://attacker.invalid");
        MockHttpServletResponse wrongResponse = new MockHttpServletResponse();
        filter.doFilter(wrong, wrongResponse, new MockFilterChain());
        assertThat(wrongResponse.getStatus()).isEqualTo(403);

        MockHttpServletRequest allowed = request();
        allowed.addHeader("X-MeetingMind-Test-Principal", BFF_PRINCIPAL);
        MockHttpServletResponse allowedResponse = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(allowed, allowedResponse, chain);
        assertThat(chain.getRequest()).isSameAs(allowed);
    }

    @Test
    void productionProfileIgnoresTheTestHeader() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        CoreWorkloadIdentityFilter filter = new CoreWorkloadIdentityFilter(
                Set.of(BFF_PRINCIPAL), true, environment, new ObjectMapper());
        MockHttpServletRequest request = request();
        request.addHeader("X-MeetingMind-Test-Principal", BFF_PRINCIPAL);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void acceptsOneAllowedCertificatePrincipalAndRejectsAmbiguousIdentity() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        CoreWorkloadIdentityFilter filter = new CoreWorkloadIdentityFilter(
                Set.of(BFF_PRINCIPAL), false, environment, new ObjectMapper());
        MockHttpServletRequest allowed = request();
        allowed.setAttribute(
                "jakarta.servlet.request.X509Certificate",
                new X509Certificate[]{certificate(List.of(List.of(6, BFF_PRINCIPAL)))});
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(allowed, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(allowed);

        MockHttpServletRequest ambiguous = request();
        ambiguous.setAttribute(
                "jakarta.servlet.request.X509Certificate",
                new X509Certificate[]{certificate(List.of(
                        List.of(6, BFF_PRINCIPAL),
                        List.of(6, "spiffe://meetingmind.internal/ns/meetingmind/sa/attacker")))});
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(ambiguous, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    private X509Certificate certificate(List<List<?>> subjectAlternativeNames) throws Exception {
        X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getSubjectAlternativeNames()).thenReturn(subjectAlternativeNames);
        return certificate;
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/internal/v1/users/projection");
        return request;
    }
}
