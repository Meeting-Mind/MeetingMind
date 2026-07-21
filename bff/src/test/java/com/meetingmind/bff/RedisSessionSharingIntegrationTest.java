package com.meetingmind.bff;

import static org.assertj.core.api.Assertions.assertThat;

import com.meetingmind.bff.auth.BffAuthUser;
import com.meetingmind.bff.auth.BffSessionAttributes;
import com.meetingmind.bff.auth.BffSessionManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@EnabledIfEnvironmentVariable(named = "BFF_REDIS_INTEGRATION", matches = "true")
class RedisSessionSharingIntegrationTest {

    private static final String AUTH_USER_ID = "0a5b7c1e-5d75-4dc0-a10e-a330d0583930";

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

    @Test
    void invalidatesEveryRedisSessionIndexedForOneAuthUser() {
        String namespace = "meetingmind:bff:logout-all:" + UUID.randomUUID();

        try (ConfigurableApplicationContext context = application(namespace)) {
            SessionRepository<Session> repository = sessionRepository(context);
            Session first = authenticatedSession(repository);
            Session second = authenticatedSession(repository);
            assertThat(repository).isInstanceOf(FindByIndexNameSessionRepository.class);
            assertThat(indexed(repository).findByIndexNameAndIndexValue(
                    FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                    AUTH_USER_ID)).containsKeys(first.getId(), second.getId());

            context.getBean(BffSessionManager.class).invalidateUserSessions(AUTH_USER_ID);

            assertThat(repository.findById(first.getId())).isNull();
            assertThat(repository.findById(second.getId())).isNull();
        }
    }

    private Session authenticatedSession(SessionRepository<Session> repository) {
        Session session = repository.createSession();
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new BffAuthUser(AUTH_USER_ID, "member@meetingmind.test", "Member", null, "ACTIVE"),
                null,
                java.util.List.of()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        session.setAttribute(BffSessionAttributes.USER_ID, AUTH_USER_ID);
        session.setAttribute(BffSessionAttributes.TOKEN_BUNDLE_ID, UUID.randomUUID());
        session.setAttribute(BffSessionAttributes.ABSOLUTE_EXPIRES_AT, Instant.now().plusSeconds(600));
        repository.save(session);
        return session;
    }

    @SuppressWarnings("unchecked")
    private FindByIndexNameSessionRepository<Session> indexed(SessionRepository<Session> repository) {
        return (FindByIndexNameSessionRepository<Session>) repository;
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
