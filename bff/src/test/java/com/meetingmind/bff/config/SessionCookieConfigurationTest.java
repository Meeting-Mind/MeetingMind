package com.meetingmind.bff.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;

class SessionCookieConfigurationTest {

    private final SessionCookieConfiguration configuration = new SessionCookieConfiguration();

    @Test
    void writesProductionHostCookieWithStrictSecurityFlags() {
        String setCookie = writeCookie(configuration.sessionCookieSerializer("__Host-mm-session", true));

        assertThat(setCookie)
                .startsWith("__Host-mm-session=")
                .contains("Path=/")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .doesNotContain("Domain=");
    }

    @Test
    void writesLocalHostOnlyCookieWithoutSecureFlag() {
        String setCookie = writeCookie(configuration.sessionCookieSerializer("mm-session", false));

        assertThat(setCookie)
                .startsWith("mm-session=")
                .contains("Path=/")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .doesNotContain("Secure")
                .doesNotContain("Domain=");
    }

    @Test
    void writesPersistentCookieOnlyWhenTheLoginRequestProvidesAnAbsoluteMaxAge() {
        CookieSerializer serializer = configuration.sessionCookieSerializer("mm-session", false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(SessionCookieConfiguration.COOKIE_MAX_AGE_REQUEST_ATTRIBUTE, 1_209_600);
        MockHttpServletResponse response = new MockHttpServletResponse();

        serializer.writeCookieValue(new CookieValue(request, response, "session-id"));

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=1209600");
    }

    @Test
    void doesNotOverrideCookieDeletionWithRememberMeMaxAge() {
        CookieSerializer serializer = configuration.sessionCookieSerializer("mm-session", false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(SessionCookieConfiguration.COOKIE_MAX_AGE_REQUEST_ATTRIBUTE, 1_209_600);
        MockHttpServletResponse response = new MockHttpServletResponse();
        CookieValue cookieValue = new CookieValue(request, response, "");
        cookieValue.setCookieMaxAge(0);

        serializer.writeCookieValue(cookieValue);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("Max-Age=0")
                .doesNotContain("Max-Age=1209600");
    }

    private String writeCookie(CookieSerializer serializer) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        serializer.writeCookieValue(new CookieValue(request, response, "session-id"));
        return response.getHeader(HttpHeaders.SET_COOKIE);
    }
}
