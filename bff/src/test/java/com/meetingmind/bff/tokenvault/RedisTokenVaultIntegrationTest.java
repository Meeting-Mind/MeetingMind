package com.meetingmind.bff.tokenvault;

import static org.assertj.core.api.Assertions.assertThat;

import com.meetingmind.bff.MeetingMindBffApplication;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;

@EnabledIfEnvironmentVariable(named = "BFF_REDIS_INTEGRATION", matches = "true")
class RedisTokenVaultIntegrationTest {

    private static final String LOCAL_KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

    @Test
    void persistsOnlyCiphertextAndAtomicallyRotatesTheBundle() {
        String namespace = "meetingmind:bff:test-token-vault:" + UUID.randomUUID();
        try (ConfigurableApplicationContext context = application(namespace)) {
            TokenVault vault = context.getBean(TokenVault.class);
            StringRedisTemplate redis = context.getBean(StringRedisTemplate.class);
            UUID bundleId = UUID.randomUUID();
            UUID authSessionId = UUID.randomUUID();
            TokenBundlePayload first = payload(authSessionId, "access-plain-v1", "refresh-plain-v1", 900);

            EncryptedTokenBundle created = vault.create(bundleId, first);
            String rawCreated = redis.opsForValue().get(namespace + ":" + bundleId);
            assertThat(rawCreated)
                    .isNotNull()
                    .doesNotContain(first.accessToken())
                    .doesNotContain(first.refreshToken());

            TokenBundlePayload second = payload(authSessionId, "access-plain-v2", "refresh-plain-v2", 1800);
            EncryptedTokenBundle rotated = vault.rotate(bundleId, created.version(), second);
            String rawRotated = redis.opsForValue().get(namespace + ":" + bundleId);
            assertThat(rotated.version()).isEqualTo(2);
            assertThat(rawRotated)
                    .isNotNull()
                    .doesNotContain(first.accessToken())
                    .doesNotContain(first.refreshToken())
                    .doesNotContain(second.accessToken())
                    .doesNotContain(second.refreshToken());
            assertThat(vault.read(bundleId, authSessionId)).isEqualTo(second);

            vault.delete(bundleId);
            assertThat(redis.hasKey(namespace + ":" + bundleId)).isFalse();
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
                        "--meetingmind.bff.token-vault.namespace=" + namespace,
                        "--meetingmind.bff.token-vault.local-key-id=redis-integration-test",
                        "--meetingmind.bff.token-vault.local-master-key-base64=" + LOCAL_KEY,
                        "--management.health.redis.enabled=false");
    }

    private TokenBundlePayload payload(
            UUID authSessionId, String accessToken, String refreshToken, long accessLifetimeSeconds) {
        Instant now = Instant.now();
        return new TokenBundlePayload(
                authSessionId,
                accessToken,
                refreshToken,
                "Bearer",
                now.plusSeconds(accessLifetimeSeconds),
                now.plusSeconds(1209600),
                "meetingmind-auth",
                Set.of("meetingmind-core"),
                Set.of("meeting:read"));
    }

    private String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
