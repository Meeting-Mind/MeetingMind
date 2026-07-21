package com.meetingmind.bff.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.meetingmind.bff.tokenvault.TokenVault;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

class BffAbsoluteSessionExpiryFilterTest {

    private static final Instant NOW = Instant.parse("2026-07-16T12:00:00Z");

    @Test
    void invalidatesExpiredSessionAndRejectsProtectedRequest() throws Exception {
        TokenVault tokenVault = mock(TokenVault.class);
        BffAbsoluteSessionExpiryFilter filter = new BffAbsoluteSessionExpiryFilter(
                tokenVault, Clock.fixed(NOW, ZoneOffset.UTC));
        MockHttpServletRequest request = request("/api/v1/spaces");
        UUID tokenBundleId = expiredSession(request);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(request, response, (filteredRequest, filteredResponse) -> continued.set(true));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).contains("\"code\":\"SESSION_INVALID\"");
        assertThat(continued).isFalse();
        verify(tokenVault).delete(tokenBundleId);
    }

    @Test
    void invalidatesExpiredSessionButLetsBootstrapReturnUnauthenticated() throws Exception {
        TokenVault tokenVault = mock(TokenVault.class);
        BffAbsoluteSessionExpiryFilter filter = new BffAbsoluteSessionExpiryFilter(
                tokenVault, Clock.fixed(NOW, ZoneOffset.UTC));
        MockHttpServletRequest request = request("/api/v1/auth/session");
        UUID tokenBundleId = expiredSession(request);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(tokenVault).delete(tokenBundleId);
    }

    @Test
    void invalidatesExpiredSessionButLetsLogoutRemainIdempotent() throws Exception {
        TokenVault tokenVault = mock(TokenVault.class);
        BffAbsoluteSessionExpiryFilter filter = new BffAbsoluteSessionExpiryFilter(
                tokenVault, Clock.fixed(NOW, ZoneOffset.UTC));
        MockHttpServletRequest request = request("/api/v1/auth/logout");
        UUID tokenBundleId = expiredSession(request);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(tokenVault).delete(tokenBundleId);
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        return request;
    }

    private UUID expiredSession(MockHttpServletRequest request) {
        MockHttpSession session = new MockHttpSession();
        UUID tokenBundleId = UUID.randomUUID();
        session.setAttribute(BffSessionAttributes.USER_ID, "user-id");
        session.setAttribute(BffSessionAttributes.TOKEN_BUNDLE_ID, tokenBundleId);
        session.setAttribute(BffSessionAttributes.ABSOLUTE_EXPIRES_AT, NOW.minusSeconds(1));
        request.setSession(session);
        return tokenBundleId;
    }
}
