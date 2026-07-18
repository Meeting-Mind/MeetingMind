package com.meetingmind.bff.config;

import com.meetingmind.bff.proxy.DownstreamService;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meetingmind.bff.downstream")
public record DownstreamProxyProperties(
        ServicePolicy core,
        ServicePolicy ai,
        ServicePolicy livekit) {

    public DownstreamProxyProperties {
        if (core == null || ai == null || livekit == null) {
            throw new IllegalArgumentException("all downstream service policies are required");
        }
    }

    public ServicePolicy policy(DownstreamService service) {
        return switch (service) {
            case CORE -> core;
            case AI -> ai;
            case LIVEKIT -> livekit;
        };
    }

    public record ServicePolicy(
            URI baseUrl,
            Duration connectTimeout,
            Duration readTimeout,
            int maxConcurrent,
            int failureThreshold,
            Duration openDuration) {

        public ServicePolicy {
            validateOrigin(baseUrl);
            requirePositive("connectTimeout", connectTimeout);
            requirePositive("readTimeout", readTimeout);
            requirePositive("openDuration", openDuration);
            if (maxConcurrent <= 0 || failureThreshold <= 0) {
                throw new IllegalArgumentException("downstream limits must be positive");
            }
        }

        private static void validateOrigin(URI uri) {
            if (uri == null
                    || (!("http".equals(uri.getScheme())) && !("https".equals(uri.getScheme())))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || !(uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
                throw new IllegalArgumentException("downstream baseUrl must be an http(s) origin");
            }
        }

        private static void requirePositive(String name, Duration value) {
            if (value == null || value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException(name + " must be positive");
            }
        }
    }
}
