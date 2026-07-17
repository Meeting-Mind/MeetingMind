package com.meetingmind.bff.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meetingmind.bff.session-lifetime")
public record BffSessionLifetimePolicy(
        Duration standardIdle,
        Duration standardAbsolute,
        Duration rememberIdle,
        Duration rememberAbsolute) {

    public BffSessionLifetimePolicy {
        requirePositive("standardIdle", standardIdle);
        requirePositive("standardAbsolute", standardAbsolute);
        requirePositive("rememberIdle", rememberIdle);
        requirePositive("rememberAbsolute", rememberAbsolute);
        requireWithinAbsolute("standardIdle", standardIdle, standardAbsolute);
        requireWithinAbsolute("rememberIdle", rememberIdle, rememberAbsolute);
    }

    private static void requirePositive(String name, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireWithinAbsolute(String name, Duration idle, Duration absolute) {
        if (idle.compareTo(absolute) > 0) {
            throw new IllegalArgumentException(name + " must not exceed its absolute lifetime");
        }
    }
}
