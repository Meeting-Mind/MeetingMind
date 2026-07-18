package com.meetingmind.bff.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration(proxyBeanMethods = false)
public class SessionCookieConfiguration {

    public static final String COOKIE_MAX_AGE_REQUEST_ATTRIBUTE =
            "meetingmind.bff.session-cookie.max-age";

    @Bean
    CookieSerializer sessionCookieSerializer(
            @Value("${meetingmind.bff.session-cookie.name}") String cookieName,
            @Value("${meetingmind.bff.session-cookie.secure}") boolean secure) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(cookieName);
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secure);
        serializer.setSameSite("Strict");
        return new PerRequestMaxAgeCookieSerializer(serializer);
    }

    private static final class PerRequestMaxAgeCookieSerializer implements CookieSerializer {

        private final CookieSerializer delegate;

        private PerRequestMaxAgeCookieSerializer(CookieSerializer delegate) {
            this.delegate = delegate;
        }

        @Override
        public void writeCookieValue(CookieValue cookieValue) {
            Object requestedMaxAge =
                    cookieValue.getRequest().getAttribute(COOKIE_MAX_AGE_REQUEST_ATTRIBUTE);
            if (cookieValue.getCookieMaxAge() < 0 && requestedMaxAge instanceof Integer maxAge && maxAge > 0) {
                cookieValue.setCookieMaxAge(maxAge);
            }
            delegate.writeCookieValue(cookieValue);
        }

        @Override
        public List<String> readCookieValues(HttpServletRequest request) {
            return delegate.readCookieValues(request);
        }
    }
}
