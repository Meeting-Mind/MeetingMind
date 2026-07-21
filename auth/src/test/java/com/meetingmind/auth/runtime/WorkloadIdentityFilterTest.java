package com.meetingmind.auth.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class WorkloadIdentityFilterTest {

    @Test
    void neverAcceptsTestPrincipalHeaderInProductionProfile() throws Exception {
        String principal = "spiffe://meetingmind.internal/ns/meetingmind/sa/meetingmind-bff";
        AuthRuntimeProperties properties = new AuthRuntimeProperties(
                "test-only-refresh-hash-secret-32-bytes-minimum",
                Duration.ofDays(14),
                10,
                Duration.ofMinutes(10),
                Duration.ofMinutes(11),
                new AuthRuntimeProperties.Google(
                        List.of("test-client"),
                        URI.create("https://example.invalid/jwks"),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1),
                        Duration.ofHours(1)
                ),
                new AuthRuntimeProperties.Workload(Set.of(principal), Set.of(principal), true),
                new AuthRuntimeProperties.WithdrawalReconciliation(false, "", Duration.ofSeconds(30), "")
        );
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        WorkloadIdentityFilter filter = new WorkloadIdentityFilter(properties, environment);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/internal/v1/auth/login"
        );
        request.addHeader("X-MeetingMind-Test-Principal", principal);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"WORKLOAD_AUTH_REQUIRED\"");
    }

    @Test
    void appliesSeparateJwksPrincipalAllowlist() throws Exception {
        String bff = "spiffe://meetingmind.internal/ns/meetingmind/sa/meetingmind-bff";
        String core = "spiffe://meetingmind.internal/ns/meetingmind/sa/meetingmind-core";
        AuthRuntimeProperties properties = new AuthRuntimeProperties(
                "test-only-refresh-hash-secret-32-bytes-minimum",
                Duration.ofDays(14),
                10,
                Duration.ofMinutes(10),
                Duration.ofMinutes(11),
                new AuthRuntimeProperties.Google(
                        List.of("test-client"),
                        URI.create("https://example.invalid/jwks"),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1),
                        Duration.ofHours(1)
                ),
                new AuthRuntimeProperties.Workload(Set.of(bff), Set.of(core), true),
                new AuthRuntimeProperties.WithdrawalReconciliation(false, "", Duration.ofSeconds(30), "")
        );
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        WorkloadIdentityFilter filter = new WorkloadIdentityFilter(properties, environment);
        MockHttpServletRequest deniedRequest = new MockHttpServletRequest(
                "GET",
                "/.well-known/jwks.json"
        );
        deniedRequest.addHeader("X-MeetingMind-Test-Principal", bff);
        MockHttpServletResponse deniedResponse = new MockHttpServletResponse();

        filter.doFilter(deniedRequest, deniedResponse, new MockFilterChain());

        assertThat(deniedResponse.getStatus()).isEqualTo(403);
        assertThat(deniedResponse.getContentAsString()).contains("\"code\":\"WORKLOAD_FORBIDDEN\"");

        MockHttpServletRequest allowedRequest = new MockHttpServletRequest(
                "GET",
                "/.well-known/jwks.json"
        );
        allowedRequest.addHeader("X-MeetingMind-Test-Principal", core);
        MockHttpServletResponse allowedResponse = new MockHttpServletResponse();
        filter.doFilter(allowedRequest, allowedResponse, new MockFilterChain());
        assertThat(allowedResponse.getStatus()).isEqualTo(200);
    }
}
