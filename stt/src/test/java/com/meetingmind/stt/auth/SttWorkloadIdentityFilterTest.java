package com.meetingmind.stt.auth;

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

class SttWorkloadIdentityFilterTest {

    private static final String CORE_PRINCIPAL =
            "spiffe://meetingmind.internal/ns/meetingmind/sa/meetingmind-core";

    @Test
    void acceptsOneAllowedCertificatePrincipalAndRejectsWrongPrincipal() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        SttWorkloadIdentityFilter filter = new SttWorkloadIdentityFilter(
                Set.of(CORE_PRINCIPAL), false, environment, new ObjectMapper());
        MockHttpServletRequest allowed = request();
        allowed.setAttribute(
                "jakarta.servlet.request.X509Certificate",
                new X509Certificate[]{certificate(CORE_PRINCIPAL)});
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(allowed, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(allowed);

        MockHttpServletRequest wrong = request();
        wrong.setAttribute(
                "jakarta.servlet.request.X509Certificate",
                new X509Certificate[]{certificate(
                        "spiffe://meetingmind.internal/ns/meetingmind/sa/attacker")});
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(wrong, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void productionProfileIgnoresTestPrincipalHeader() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        SttWorkloadIdentityFilter filter = new SttWorkloadIdentityFilter(
                Set.of(CORE_PRINCIPAL), true, environment, new ObjectMapper());
        MockHttpServletRequest request = request();
        request.addHeader("X-MeetingMind-Test-Principal", CORE_PRINCIPAL);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/internal/v1/transcriptions");
        return request;
    }

    private X509Certificate certificate(String principal) throws Exception {
        X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getSubjectAlternativeNames())
                .thenReturn(List.of(List.of(6, principal)));
        return certificate;
    }
}
