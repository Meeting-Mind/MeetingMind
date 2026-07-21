package com.meetingmind.demo.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("db")
@EnabledIfEnvironmentVariable(named = "CI_POSTGRES_URL", matches = ".+")
@Transactional
class JdbcAuthStoreIntegrationTest {

    @Autowired
    private AuthStore store;

    @Autowired
    private AuthService service;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CI_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("CI_POSTGRES_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("CI_POSTGRES_PASSWORD"));
    }

    @Test
    void persistsIdentityAndLocksRefreshSessionForRotation() {
        assertThat(store).isInstanceOf(JdbcAuthStore.class);

        String suffix = UUID.randomUUID().toString();
        String email = "jdbc-" + suffix + "@meetingmind.test";
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        AuthUser user = store.createUser(email, "JDBC 사용자", null, now);
        AuthIdentity identity = store.saveIdentity(user.id(), "local", email, "hashed-password", now);
        RefreshTokenSession session = store.saveRefreshSession(
                user.id(),
                "refresh-hash-" + suffix,
                now,
                now.plusSeconds(3600),
                "integration-test"
        );

        assertThat(store.findUserByEmail(email)).contains(user);
        assertThat(store.findIdentity("local", email)).contains(identity);
        assertThat(store.findRefreshSessionForUpdate(session.refreshTokenHash())).contains(session);

        store.revokeRefreshSession(session.refreshTokenHash(), now.plusSeconds(1));

        assertThat(store.findRefreshSessionForUpdate(session.refreshTokenHash()))
                .get()
                .extracting(RefreshTokenSession::revokedAt)
                .isEqualTo(now.plusSeconds(1));
    }

    @Test
    void signupLoginAndRefreshUseJdbcTransactions() {
        String email = "auth-service-" + UUID.randomUUID() + "@meetingmind.test";

        AuthTokenResponse signup = service.signup(
                new SignupRequest(email, "password-123", "JDBC Auth"),
                "integration-test"
        );
        AuthTokenResponse login = service.login(
                new LoginRequest(email.toUpperCase(), "password-123"),
                "integration-test"
        );
        AuthTokenResponse refreshed = service.refresh(
                new RefreshTokenRequest(signup.refreshToken()),
                "integration-test"
        );

        assertThat(login.user().id()).isEqualTo(signup.user().id());
        assertThat(refreshed.user().id()).isEqualTo(signup.user().id());
        assertThat(refreshed.refreshToken()).isNotEqualTo(signup.refreshToken());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.refresh(
                        new RefreshTokenRequest(signup.refreshToken()),
                        "integration-test"
                ))
                .isInstanceOf(AuthException.class)
                .satisfies(error -> assertThat(((AuthException) error).code()).isEqualTo("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void authProjectionIsIdempotentAndRejectsOwnershipConflicts() {
        UUID authUserId = UUID.randomUUID();
        String resourceUserId = "user-" + authUserId;
        String email = "projection-" + authUserId + "@meetingmind.test";
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        AuthUser created = store.upsertAuthProjection(
                authUserId,
                resourceUserId,
                email,
                "Projection User",
                null,
                "active",
                now);
        AuthUser updated = store.upsertAuthProjection(
                authUserId,
                resourceUserId,
                email,
                "Projection User Updated",
                null,
                "active",
                now.plusSeconds(1));

        assertThat(created.id()).isEqualTo(resourceUserId);
        assertThat(updated.displayName()).isEqualTo("Projection User Updated");
        assertThat(store.findUserByAuthUserId(authUserId))
                .get()
                .extracting(AuthUser::id)
                .isEqualTo(resourceUserId);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> store.upsertAuthProjection(
                        UUID.randomUUID(),
                        resourceUserId,
                        "conflict-" + email,
                        "Conflict",
                        null,
                        "active",
                        now))
                .isInstanceOfSatisfying(
                        AuthException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("USER_PROJECTION_CONFLICT"));
    }
}
