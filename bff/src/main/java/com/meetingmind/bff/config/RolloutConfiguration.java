package com.meetingmind.bff.config;

import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RolloutProperties.class)
public class RolloutConfiguration {

    @Bean(name = "rollout")
    HealthIndicator rolloutHealthIndicator(RolloutProperties properties) {
        return () -> properties.acceptBrowserTraffic()
                ? org.springframework.boot.actuate.health.Health.up().build()
                : org.springframework.boot.actuate.health.Health.down()
                        .withDetail("reason", "browser_traffic_disabled")
                        .build();
    }
}
