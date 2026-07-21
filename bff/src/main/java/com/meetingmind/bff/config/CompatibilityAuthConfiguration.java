package com.meetingmind.bff.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.bff.auth.CompatibilityAuthClient;
import com.meetingmind.bff.auth.LegacyBackendAuthClient;
import com.meetingmind.bff.auth.TargetAuthServiceClient;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class CompatibilityAuthConfiguration {

    @Bean
    @ConditionalOnProperty(name = "meetingmind.bff.auth.mode", havingValue = "legacy", matchIfMissing = true)
    RestClient compatibilityAuthRestClient(
            RestClient.Builder builder,
            @Value("${meetingmind.bff.compat-auth.base-url}") String baseUrl) {
        URI uri = validateBaseUrl(baseUrl);
        return builder.baseUrl(uri.toString()).build();
    }

    @Bean
    @ConditionalOnProperty(name = "meetingmind.bff.auth.mode", havingValue = "legacy", matchIfMissing = true)
    CompatibilityAuthClient compatibilityAuthClient(
            RestClient compatibilityAuthRestClient, ObjectMapper objectMapper) {
        return new LegacyBackendAuthClient(compatibilityAuthRestClient, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "meetingmind.bff.auth.mode", havingValue = "target")
    RestClient targetAuthRestClient(
            RestClient.Builder builder,
            @Value("${meetingmind.bff.auth.base-url}") String baseUrl) {
        return builder.baseUrl(validateBaseUrl(baseUrl).toString()).build();
    }

    @Bean
    @ConditionalOnProperty(name = "meetingmind.bff.auth.mode", havingValue = "target")
    RestClient targetCoreProjectionRestClient(
            RestClient.Builder builder,
            @Value("${meetingmind.bff.downstream.core.base-url}") String baseUrl) {
        return builder.baseUrl(validateBaseUrl(baseUrl).toString()).build();
    }

    @Bean
    @ConditionalOnProperty(name = "meetingmind.bff.auth.mode", havingValue = "target")
    CompatibilityAuthClient targetAuthClient(
            @Qualifier("targetAuthRestClient") RestClient targetAuthRestClient,
            @Qualifier("targetCoreProjectionRestClient") RestClient targetCoreProjectionRestClient,
            ObjectMapper objectMapper,
            Environment environment,
            @Value("${meetingmind.bff.auth.test-workload-principal:}") String testWorkloadPrincipal) {
        if (testWorkloadPrincipal != null
                && !testWorkloadPrincipal.isBlank()
                && !environment.acceptsProfiles(Profiles.of("local", "test", "integration"))) {
            throw new IllegalStateException("test workload principal is only allowed in local/test/integration profiles");
        }
        return new TargetAuthServiceClient(
                targetAuthRestClient,
                targetCoreProjectionRestClient,
                objectMapper,
                testWorkloadPrincipal);
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
}
