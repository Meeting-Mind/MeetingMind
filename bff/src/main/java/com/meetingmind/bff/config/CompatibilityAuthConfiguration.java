package com.meetingmind.bff.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.bff.auth.CompatibilityAuthClient;
import com.meetingmind.bff.auth.LegacyBackendAuthClient;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class CompatibilityAuthConfiguration {

    @Bean
    RestClient compatibilityAuthRestClient(
            RestClient.Builder builder,
            @Value("${meetingmind.bff.compat-auth.base-url}") String baseUrl) {
        URI uri = validateBaseUrl(baseUrl);
        return builder.baseUrl(uri.toString()).build();
    }

    @Bean
    CompatibilityAuthClient compatibilityAuthClient(
            RestClient compatibilityAuthRestClient, ObjectMapper objectMapper) {
        return new LegacyBackendAuthClient(compatibilityAuthRestClient, objectMapper);
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
