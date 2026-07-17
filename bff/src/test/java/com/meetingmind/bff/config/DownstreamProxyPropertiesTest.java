package com.meetingmind.bff.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meetingmind.bff.config.DownstreamProxyProperties.ServicePolicy;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DownstreamProxyPropertiesTest {

    @Test
    void acceptsOnlyHttpOriginsAsConfiguredDestinations() {
        assertThatThrownBy(() -> policy("https://user@core.example"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy("https://core.example/internal"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy("file:///tmp/backend"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ServicePolicy policy(String origin) {
        return new ServicePolicy(
                URI.create(origin),
                Duration.ofSeconds(1),
                Duration.ofSeconds(3),
                2,
                2,
                Duration.ofSeconds(10));
    }
}
