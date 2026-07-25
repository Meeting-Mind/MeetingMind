package com.meetingmind.demo.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.demo.auth.target.HttpJwksSource;
import com.meetingmind.demo.auth.target.TargetAccessTokenValidator;
import com.meetingmind.demo.config.InternalHttpClientFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration(proxyBeanMethods = false)
class CoreAccessTokenConfiguration {

    @Bean
    Clock coreAuthClock() {
        return Clock.systemUTC();
    }

    @Bean
    TargetAccessTokenValidator targetAccessTokenValidator(
            ObjectMapper objectMapper,
            Clock coreAuthClock,
            @Value("${meetingmind.auth.target.issuer}") String issuer,
            @Value("${meetingmind.auth.target.audience}") String audience,
            @Value("${meetingmind.auth.target.jwks-uri}") URI jwksUri,
            @Value("${meetingmind.auth.target.jwks-request-timeout}") Duration requestTimeout,
            @Value("${meetingmind.auth.target.allow-test-principal-header:false}")
                    boolean allowTestPrincipalHeader,
            @Value("${meetingmind.auth.target.test-principal:}") String testPrincipal,
            InternalHttpClientFactory internalHttpClientFactory,
            Environment environment) {
        boolean testHeaderAllowed = allowTestPrincipalHeader
                && environment.acceptsProfiles(Profiles.of(
                        "local", "test", "integration", "db"));
        Map<String, String> headers = testHeaderAllowed
                ? Map.of("X-MeetingMind-Test-Principal", requireTestPrincipal(testPrincipal))
                : Map.of();
        HttpClient client = internalHttpClientFactory.newBuilder()
                .connectTimeout(requestTimeout)
                .build();
        return new TargetAccessTokenValidator(
                issuer,
                audience,
                new HttpJwksSource(jwksUri, client, requestTimeout, headers),
                objectMapper,
                coreAuthClock);
    }

    @Bean
    AccessTokenSubjectResolver accessTokenSubjectResolver(
            AuthTokenService legacyValidator,
            TargetAccessTokenValidator targetValidator,
            ObjectMapper objectMapper,
            @Value("${meetingmind.auth.validation-mode}") String validationMode) {
        try {
            return new AccessTokenSubjectResolver(
                    AccessTokenSubjectResolver.Mode.valueOf(validationMode.trim().toUpperCase()),
                    legacyValidator,
                    targetValidator,
                    objectMapper);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "MEETINGMIND_AUTH_VALIDATION_MODE must be LEGACY_ONLY, DUAL, or TARGET_ONLY");
        }
    }

    private String requireTestPrincipal(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Core Auth test principal is required when enabled");
        }
        return value;
    }
}
