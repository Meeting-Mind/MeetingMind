package com.meetingmind.bff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

@EnabledIfEnvironmentVariable(named = "BFF_REDIS_INTEGRATION", matches = "true")
class RedisSessionSharingIntegrationTest {

    @Test
    void sharesSessionAcrossApplicationInstances() {
        String namespace = "meetingmind:bff:test:" + UUID.randomUUID();

        try (ConfigurableApplicationContext first = application(namespace);
                ConfigurableApplicationContext second = application(namespace)) {
            SessionRepository<Session> firstRepository = sessionRepository(first);
            SessionRepository<Session> secondRepository = sessionRepository(second);

            Session created = firstRepository.createSession();
            created.setAttribute("sourceInstance", "first");
            firstRepository.save(created);

            Session loaded = secondRepository.findById(created.getId());

            assertThat(loaded).isNotNull();
            assertThat(loaded.<String>getAttribute("sourceInstance")).isEqualTo("first");
            secondRepository.deleteById(created.getId());
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
                        "--spring.session.redis.namespace=" + namespace,
                        "--meetingmind.bff.token-vault.key-provider=local",
                        "--meetingmind.bff.token-vault.local-key-id=redis-integration-test",
                        "--meetingmind.bff.token-vault.local-master-key-base64="
                                + "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
                        "--management.health.redis.enabled=false");
    }

    @SuppressWarnings("unchecked")
    private SessionRepository<Session> sessionRepository(ConfigurableApplicationContext context) {
        return (SessionRepository<Session>) context.getBean(SessionRepository.class);
    }

    private String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
