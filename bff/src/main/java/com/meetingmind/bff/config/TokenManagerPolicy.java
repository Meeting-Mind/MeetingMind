package com.meetingmind.bff.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meetingmind.bff.token-manager")
public record TokenManagerPolicy(
        Duration accessExpirySkew,
        Duration lockLease,
        Duration waitTimeout,
        Duration pollInterval) {

    public TokenManagerPolicy {
        requireNonNegative("accessExpirySkew", accessExpirySkew);
        requirePositive("lockLease", lockLease);
        requirePositive("waitTimeout", waitTimeout);
        requirePositive("pollInterval", pollInterval);
        if (waitTimeout.compareTo(lockLease) < 0) {
            throw new IllegalArgumentException("waitTimeout must cover the refresh lock lease");
        }
        if (pollInterval.compareTo(waitTimeout) >= 0) {
            throw new IllegalArgumentException("pollInterval must be shorter than waitTimeout");
        }
    }

    private static void requirePositive(String name, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireNonNegative(String name, Duration value) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
