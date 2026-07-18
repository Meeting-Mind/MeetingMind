package com.meetingmind.bff.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class BffRequestMetricsFilterTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final BffRequestMetricsFilter filter =
            new BffRequestMetricsFilter(new BffRolloutMetrics(meterRegistry));

    @Test
    void recordsBoundedAuthAndProtectedOutcomesWithoutRawPaths() throws Exception {
        perform("/api/v1/auth/login", 401);
        perform("/api/v1/auth/logout", 204);
        perform("/api/v1/auth/reauthenticate", 401);
        perform("/api/v1/auth/logout-all", 403);
        perform("/api/v1/spaces/space-secret-value", 401);

        assertTimer("login", "rejected");
        assertTimer("logout", "success");
        assertTimer("reauthenticate", "rejected");
        assertTimer("logout_all", "client_error");
        assertTimer("protected", "unauthenticated");
        assertThat(meterRegistry.getMeters())
                .allSatisfy(meter -> assertThat(meter.getId().getTags())
                        .noneMatch(tag -> tag.getValue().contains("space-secret-value")));
    }

    private void perform(String path, int status) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) ->
                ((HttpServletResponse) servletResponse).setStatus(status);
        filter.doFilter(request, response, chain);
    }

    private void assertTimer(String operation, String outcome) {
        assertThat(meterRegistry.get("meetingmind.bff.browser.requests")
                        .tags("operation", operation, "outcome", outcome)
                        .timer()
                        .count())
                .isEqualTo(1L);
    }
}
