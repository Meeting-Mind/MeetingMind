package com.meetingmind.bff.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.meetingmind.bff.MeetingMindBffApplication;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@EnabledIfEnvironmentVariable(named = "BFF_REDIS_INTEGRATION", matches = "true")
class RedisRefreshSingleFlightLockIntegrationTest {

    private static final String LOCAL_KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

    @Test
    void allowsOnlyOneOwnerAndRejectsAnotherOwnersRelease() {
        String namespace = "meetingmind:bff:test-refresh-lock:" + UUID.randomUUID();
        try (ConfigurableApplicationContext context = application(namespace)) {
            RefreshSingleFlightLock lock = context.getBean(RefreshSingleFlightLock.class);
            UUID bundleId = UUID.randomUUID();

            assertThat(lock.tryAcquire(bundleId, "owner-1", Duration.ofSeconds(5))).isTrue();
            assertThat(lock.tryAcquire(bundleId, "owner-2", Duration.ofSeconds(5))).isFalse();
            lock.release(bundleId, "owner-2");
            assertThat(lock.tryAcquire(bundleId, "owner-2", Duration.ofSeconds(5))).isFalse();
            lock.release(bundleId, "owner-1");
            assertThat(lock.tryAcquire(bundleId, "owner-2", Duration.ofSeconds(5))).isTrue();
            lock.release(bundleId, "owner-2");
        }
    }

    private ConfigurableApplicationContext application(String namespace) {
        return new SpringApplicationBuilder(MeetingMindBffApplication.class)
                .profiles("redis-integration")
                .run(
                        "--server.port=0",
                        "--spring.data.redis.host=" + environment("BFF_REDIS_HOST", "127.0.0.1"),
                        "--spring.data.redis.port=" + environment("BFF_REDIS_PORT", "6380"),
                        "--spring.session.store-type=redis",
                        "--spring.session.redis.namespace=" + namespace + ":session",
                        "--meetingmind.bff.token-vault.key-provider=local",
                        "--meetingmind.bff.token-vault.namespace=" + namespace + ":vault",
                        "--meetingmind.bff.token-vault.local-key-id=redis-integration-test",
                        "--meetingmind.bff.token-vault.local-master-key-base64=" + LOCAL_KEY,
                        "--meetingmind.bff.token-manager.lock-namespace=" + namespace,
                        "--management.health.redis.enabled=false");
    }

    private String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
