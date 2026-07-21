package com.meetingmind.bff.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.bff.auth.AuthClient;
import com.meetingmind.bff.auth.CoreUserProjectionClient;
import com.meetingmind.bff.auth.LegacyBackendAuthClient;
import com.meetingmind.bff.auth.TargetAuthServiceClient;
import com.meetingmind.bff.auth.TargetCoreUserProjectionClient;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class CompatibilityAuthConfiguration {

    @Bean
    @ConditionalOnProperty(
            name = "meetingmind.bff.auth-provider",
            havingValue = "legacy",
            matchIfMissing = true)
    RestClient compatibilityAuthRestClient(
            RestClient.Builder builder,
            @Value("${meetingmind.bff.compat-auth.base-url}") String baseUrl) {
        URI uri = validateBaseUrl(baseUrl);
        return builder.baseUrl(uri.toString()).build();
    }

    @Bean
    @ConditionalOnProperty(
            name = "meetingmind.bff.auth-provider",
            havingValue = "legacy",
            matchIfMissing = true)
    AuthClient compatibilityAuthClient(
            RestClient compatibilityAuthRestClient, ObjectMapper objectMapper) {
        return new LegacyBackendAuthClient(compatibilityAuthRestClient, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "meetingmind.bff.auth-provider", havingValue = "auth-service")
    RestClient targetAuthRestClient(
            RestClient.Builder builder,
            @Value("${meetingmind.bff.target-auth.base-url}") String baseUrl) {
        return builder.baseUrl(validateBaseUrl(baseUrl).toString()).build();
    }

    @Bean
    @ConditionalOnProperty(name = "meetingmind.bff.auth-provider", havingValue = "auth-service")
    AuthClient targetAuthClient(
            RestClient targetAuthRestClient,
            ObjectMapper objectMapper,
            @Value("${meetingmind.bff.target-auth.allow-test-principal-header:false}")
                    boolean allowTestPrincipalHeader,
            @Value("${meetingmind.bff.target-auth.test-principal:}") String testPrincipal,
            Environment environment) {
        return new TargetAuthServiceClient(
                targetAuthRestClient,
                objectMapper,
                localTestHeaderAllowed(allowTestPrincipalHeader, environment),
                testPrincipal);
    }

    @Bean
    CoreUserProjectionClient coreUserProjectionClient(
            RestClient.Builder builder,
            @Value("${meetingmind.bff.core-projection.base-url}") String baseUrl,
            @Value("${meetingmind.bff.core-projection.allow-test-principal-header:false}")
                    boolean allowTestPrincipalHeader,
            @Value("${meetingmind.bff.core-projection.test-principal:}") String testPrincipal,
            Environment environment) {
        RestClient restClient = builder.baseUrl(validateBaseUrl(baseUrl).toString()).build();
        return new TargetCoreUserProjectionClient(
                restClient,
                localTestHeaderAllowed(allowTestPrincipalHeader, environment),
                testPrincipal);
    }

    private URI validateBaseUrl(String value) {
        try {
            URI uri = URI.create(value);
            boolean validScheme = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
            boolean validPath = uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath());
            if (!validScheme
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || !validPath) {
                throw new IllegalArgumentException("invalid compatibility auth base URL");
            }
            return uri;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("BFF_BACKEND_BASE_URL must be an http(s) origin");
        }
    }

    private boolean localTestHeaderAllowed(boolean configured, Environment environment) {
        return configured
                && environment.acceptsProfiles(Profiles.of(
                        "local", "test", "integration", "redis-integration"));
    }
}
