package com.meetingmind.bff.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.bff.config.BffSessionLifetimePolicy;
import com.meetingmind.bff.config.TokenManagerPolicy;
import com.meetingmind.bff.observability.BffRolloutMetrics;
import com.meetingmind.bff.tokenvault.EncryptedTokenBundle;
import com.meetingmind.bff.tokenvault.EncryptedTokenBundleStore;
import com.meetingmind.bff.tokenvault.AudienceAccessToken;
import com.meetingmind.bff.tokenvault.TokenBundlePayload;
import com.meetingmind.bff.tokenvault.TokenVault;
import com.meetingmind.bff.tokenvault.TokenVaultException;
import com.meetingmind.bff.tokenvault.crypto.AesGcmTokenPayloadCipher;
import com.meetingmind.bff.tokenvault.crypto.LocalEnvelopeKeyService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

class BffTokenManagerTest {

    private static final Instant NOW = Instant.parse("2026-07-16T00:00:00Z");
    private static final String LOCAL_KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final UUID AUTH_USER_ID =
            UUID.fromString("0a5b7c1e-5d75-4dc0-a10e-a330d0583930");
    private static final String USER_ID = "user-" + AUTH_USER_ID;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final InMemoryStore store = new InMemoryStore();
    private final TokenVault tokenVault = new TokenVault(
            store,
            new AesGcmTokenPayloadCipher(new LocalEnvelopeKeyService("test-key", LOCAL_KEY)),
            new ObjectMapper().findAndRegisterModules(),
            clock);
    private final FakeCompatibilityAuthClient authClient = new FakeCompatibilityAuthClient();
    private final InMemoryRefreshLock refreshLock = new InMemoryRefreshLock();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final BffRolloutMetrics rolloutMetrics = new BffRolloutMetrics(meterRegistry);
    private final BffSessionManager sessionManager = new BffSessionManager(
            tokenVault,
            authClient,
            tokens -> {},
            new ChangeSessionIdAuthenticationStrategy(),
            new HttpSessionSecurityContextRepository(),
            new BffSessionLifetimePolicy(
                    Duration.ofHours(1),
                    Duration.ofHours(12),
                    Duration.ofDays(7),
                    Duration.ofDays(14)),
            clock,
            "meetingmind-core-legacy");
    private final BffTokenManager tokenManager = new BffTokenManager(
            tokenVault,
            authClient,
            refreshLock,
            sessionManager,
            new TokenManagerPolicy(
                    Duration.ofSeconds(30),
                    Duration.ofMillis(500),
                    Duration.ofSeconds(1),
                    Duration.ofMillis(1)),
            clock,
            rolloutMetrics);

    @Test
    void refreshesBeforeCallingDownstreamWhenAccessIsNearExpiry() {
        SessionFixture fixture = sessionFixture(10);

        String authorization =
                tokenManager.execute(fixture.request(), "meetingmind-core", value -> value);

        assertThat(authorization).isEqualTo("Bearer refreshed-access-secret");
        assertThat(authClient.refreshCalls).hasValue(1);
        assertThat(meterRegistry.get("meetingmind.bff.refresh")
                        .tag("outcome", "success")
                        .counter()
                        .count())
                .isEqualTo(1.0);
        assertThat(tokenVault.read(fixture.bundleId(), fixture.authSessionId()).refreshToken())
                .isEqualTo("refreshed-refresh-secret");
    }

    @Test
    void concurrentRequestsShareOneRefreshAndUseTheRotatedBundle() throws Exception {
        SessionFixture first = sessionFixture(10);
        SessionFixture second = requestFor(first);
        authClient.blockRefresh = true;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> firstResult = executor.submit(() ->
                    tokenManager.execute(first.request(), "meetingmind-core", value -> value));
            assertThat(authClient.refreshEntered.await(1, TimeUnit.SECONDS)).isTrue();
            Future<String> secondResult = executor.submit(() ->
                    tokenManager.execute(second.request(), "meetingmind-core", value -> value));
            assertThat(refreshLock.contentionObserved.await(1, TimeUnit.SECONDS)).isTrue();
            authClient.allowRefresh.countDown();

            assertThat(firstResult.get(2, TimeUnit.SECONDS)).isEqualTo("Bearer refreshed-access-secret");
            assertThat(secondResult.get(2, TimeUnit.SECONDS)).isEqualTo("Bearer refreshed-access-secret");
            assertThat(authClient.refreshCalls).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void retriesTheOriginalDownstreamCallOnlyOnceAfterUnauthorized() {
        SessionFixture fixture = sessionFixture(900);
        AtomicInteger downstreamCalls = new AtomicInteger();

        String result = tokenManager.execute(fixture.request(), "meetingmind-core", authorization -> {
            if (downstreamCalls.incrementAndGet() == 1) {
                throw new DownstreamUnauthorizedException();
            }
            return authorization;
        });

        assertThat(result).isEqualTo("Bearer refreshed-access-secret");
        assertThat(downstreamCalls).hasValue(2);
        assertThat(authClient.refreshCalls).hasValue(1);
    }

    @Test
    void schemaV2UsesOnlyTheAccessTokenForTheRequestedAudience() {
        SessionFixture fixture = targetSessionFixture(600);
        String core = tokenManager.execute(
                fixture.request(), "meetingmind-core", authorization -> authorization);
        String ai = tokenManager.execute(
                fixture.request(), "meetingmind-ai", authorization -> authorization);
        String livekit = tokenManager.execute(
                fixture.request(), "meetingmind-livekit", authorization -> authorization);

        assertThat(core).isEqualTo("Bearer target-core-secret");
        assertThat(ai).isEqualTo("Bearer target-ai-secret");
        assertThat(livekit).isEqualTo("Bearer target-livekit-secret");
        assertThat(authClient.refreshCalls).hasValue(0);
    }

    @Test
    void schemaV2RefreshAtomicallyRotatesTheWholeAudienceMap() {
        SessionFixture fixture = targetSessionFixture(10);
        authClient.targetRefresh = true;

        String authorization = tokenManager.execute(
                fixture.request(), "meetingmind-ai", value -> value);
        TokenBundlePayload rotated =
                tokenVault.read(fixture.bundleId(), fixture.authSessionId());

        assertThat(authorization).isEqualTo("Bearer refreshed-ai-secret");
        assertThat(rotated.schemaVersion()).isEqualTo(2);
        assertThat(rotated.requireAccessToken("meetingmind-core").token())
                .isEqualTo("refreshed-core-secret");
        assertThat(rotated.requireAccessToken("meetingmind-livekit").token())
                .isEqualTo("refreshed-livekit-secret");
        assertThat(rotated.refreshToken()).isEqualTo("refreshed-refresh-secret");
    }

    @Test
    void finalUnauthorizedDeletesTheBundleAndInvalidatesTheSession() {
        SessionFixture fixture = sessionFixture(900);
        AtomicInteger downstreamCalls = new AtomicInteger();

        assertThatThrownBy(() -> tokenManager.execute(fixture.request(), "meetingmind-core", authorization -> {
                    downstreamCalls.incrementAndGet();
                    throw new DownstreamUnauthorizedException();
                }))
                .isInstanceOfSatisfying(BffAuthException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.code()).isEqualTo("SESSION_INVALID");
                });

        assertThat(downstreamCalls).hasValue(2);
        assertThat(authClient.refreshCalls).hasValue(1);
        assertThat(authClient.revokeCalls).hasValue(1);
        assertThat(store.bundle).isNull();
        assertThat(fixture.session().isInvalid()).isTrue();
    }

    @Test
    void refreshFailureFailsClosedAndCleansUpTheSession() {
        SessionFixture fixture = sessionFixture(10);
        authClient.refreshFailure = BffAuthException.of(
                HttpStatus.UNAUTHORIZED,
                "REFRESH_TOKEN_INVALID",
                "must not reach browser");

        assertThatThrownBy(() -> tokenManager.execute(
                        fixture.request(), "meetingmind-core", authorization -> authorization))
                .isInstanceOfSatisfying(BffAuthException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.code()).isEqualTo("SESSION_INVALID");
                    assertThat(exception.getMessage()).doesNotContain("must not reach browser");
                });

        assertThat(authClient.refreshCalls).hasValue(1);
        assertThat(meterRegistry.get("meetingmind.bff.refresh")
                        .tag("outcome", "failure")
                        .counter()
                        .count())
                .isEqualTo(1.0);
        assertThat(authClient.revokeCalls).hasValue(1);
        assertThat(store.bundle).isNull();
        assertThat(fixture.session().isInvalid()).isTrue();
    }

    @Test
    void logoutRefreshesNearExpiryRevokesRotatedTokensAndCleansUp() {
        SessionFixture fixture = sessionFixture(10);

        tokenManager.logout(fixture.request());

        assertThat(authClient.refreshCalls).hasValue(1);
        assertThat(authClient.revokeCalls).hasValue(1);
        assertThat(authClient.revokedAccessToken).isEqualTo("refreshed-access-secret");
        assertThat(authClient.revokedRefreshToken).isEqualTo("refreshed-refresh-secret");
        assertThat(store.bundle).isNull();
        assertThat(fixture.session().isInvalid()).isTrue();
    }

    @Test
    void logoutWithoutSessionIsIdempotent() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        tokenManager.logout(request);

        assertThat(authClient.refreshCalls).hasValue(0);
        assertThat(authClient.revokeCalls).hasValue(0);
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void logoutFailsClosedWhenRefreshFails() {
        SessionFixture fixture = sessionFixture(10);
        authClient.refreshFailure = new IllegalStateException("auth unavailable");

        tokenManager.logout(fixture.request());

        assertThat(authClient.refreshCalls).hasValue(1);
        assertThat(authClient.revokeCalls).hasValue(0);
        assertThat(store.bundle).isNull();
        assertThat(fixture.session().isInvalid()).isTrue();
    }

    private SessionFixture sessionFixture(long accessLifetimeSeconds) {
        UUID authSessionId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        tokenVault.create(bundleId, new TokenBundlePayload(
                authSessionId,
                AuthTokenResponse.LEGACY_SCHEMA_VERSION,
                Map.of(
                        AuthTokenResponse.LEGACY_AUDIENCE,
                        new AudienceAccessToken(
                                "initial-access-secret",
                                NOW.plusSeconds(accessLifetimeSeconds))),
                "initial-refresh-secret",
                "Bearer",
                NOW.plus(Duration.ofDays(14)),
                "meetingmind-core-legacy",
                Map.of()));
        return request(authSessionId, bundleId);
    }

    private SessionFixture targetSessionFixture(long accessLifetimeSeconds) {
        UUID authSessionId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        Instant expiresAt = NOW.plusSeconds(accessLifetimeSeconds);
        tokenVault.create(bundleId, new TokenBundlePayload(
                authSessionId,
                AuthTokenResponse.TARGET_SCHEMA_VERSION,
                Map.of(
                        "meetingmind-core",
                        new AudienceAccessToken("target-core-secret", expiresAt),
                        "meetingmind-ai",
                        new AudienceAccessToken("target-ai-secret", expiresAt),
                        "meetingmind-livekit",
                        new AudienceAccessToken("target-livekit-secret", expiresAt)),
                "target-refresh-secret",
                "Bearer",
                NOW.plus(Duration.ofDays(14)),
                "https://auth.meetingmind.internal",
                Map.of()));
        return request(authSessionId, bundleId);
    }

    private SessionFixture requestFor(SessionFixture fixture) {
        return request(fixture.authSessionId(), fixture.bundleId());
    }

    private SessionFixture request(UUID authSessionId, UUID bundleId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(BffSessionAttributes.RESOURCE_USER_ID, USER_ID);
        session.setAttribute(BffSessionAttributes.AUTH_USER_ID, AUTH_USER_ID);
        session.setAttribute(BffSessionAttributes.AUTH_SESSION_ID, authSessionId);
        session.setAttribute(BffSessionAttributes.TOKEN_BUNDLE_ID, bundleId);
        session.setAttribute(BffSessionAttributes.ABSOLUTE_EXPIRES_AT, NOW.plus(Duration.ofHours(12)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        request.addHeader("User-Agent", "JUnit");
        return new SessionFixture(request, session, authSessionId, bundleId);
    }

    private record SessionFixture(
            MockHttpServletRequest request,
            MockHttpSession session,
            UUID authSessionId,
            UUID bundleId) {}

    private static final class InMemoryStore implements EncryptedTokenBundleStore {

        private EncryptedTokenBundle bundle;

        @Override
        public synchronized void create(EncryptedTokenBundle bundle) {
            if (this.bundle != null) {
                throw TokenVaultException.of(TokenVaultException.Code.BUNDLE_ALREADY_EXISTS);
            }
            this.bundle = bundle;
        }

        @Override
        public synchronized Optional<EncryptedTokenBundle> findById(UUID bundleId) {
            return bundle != null && bundle.id().equals(bundleId) ? Optional.of(bundle) : Optional.empty();
        }

        @Override
        public synchronized boolean replace(long expectedVersion, EncryptedTokenBundle replacement) {
            if (bundle == null || bundle.version() != expectedVersion) {
                return false;
            }
            bundle = replacement;
            return true;
        }

        @Override
        public synchronized void deleteById(UUID bundleId) {
            if (bundle != null && bundle.id().equals(bundleId)) {
                bundle = null;
            }
        }
    }

    private static final class InMemoryRefreshLock implements RefreshSingleFlightLock {

        private final AtomicReference<String> owner = new AtomicReference<>();
        private final CountDownLatch contentionObserved = new CountDownLatch(1);

        @Override
        public boolean tryAcquire(UUID tokenBundleId, String candidate, Duration lease) {
            boolean acquired = owner.compareAndSet(null, candidate);
            if (!acquired) {
                contentionObserved.countDown();
            }
            return acquired;
        }

        @Override
        public void release(UUID tokenBundleId, String candidate) {
            owner.compareAndSet(candidate, null);
        }
    }

    private static final class FakeCompatibilityAuthClient implements AuthClient {

        private final AtomicInteger refreshCalls = new AtomicInteger();
        private final AtomicInteger revokeCalls = new AtomicInteger();
        private final CountDownLatch refreshEntered = new CountDownLatch(1);
        private final CountDownLatch allowRefresh = new CountDownLatch(1);
        private volatile boolean blockRefresh;
        private volatile RuntimeException refreshFailure;
        private volatile boolean targetRefresh;
        private volatile String revokedAccessToken;
        private volatile String revokedRefreshToken;

        @Override
        public AuthTokenResponse signup(BrowserAuthRequests.Signup request, String userAgent) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AuthTokenResponse login(BrowserAuthRequests.Login request, String userAgent) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AuthTokenResponse google(BrowserAuthRequests.Google request, String userAgent) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AuthTokenResponse refresh(
                UUID authSessionId, String refreshToken, String userAgent) {
            refreshCalls.incrementAndGet();
            refreshEntered.countDown();
            if (blockRefresh) {
                try {
                    if (!allowRefresh.await(1, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test refresh timed out");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("test refresh interrupted");
                }
            }
            if (refreshFailure != null) {
                throw refreshFailure;
            }
            if (targetRefresh) {
                return new AuthTokenResponse(
                        AuthTokenResponse.TARGET_SCHEMA_VERSION,
                        authSessionId,
                        Map.of(
                                "meetingmind-core",
                                new AuthTokenResponse.AccessToken(
                                        "refreshed-core-secret", 600),
                                "meetingmind-ai",
                                new AuthTokenResponse.AccessToken(
                                        "refreshed-ai-secret", 600),
                                "meetingmind-livekit",
                                new AuthTokenResponse.AccessToken(
                                        "refreshed-livekit-secret", 600)),
                        "refreshed-refresh-secret",
                        "Bearer",
                        1_209_600,
                        new AuthTokenResponse.User(
                                AUTH_USER_ID,
                                USER_ID,
                                "user@example.com",
                                "User",
                                null,
                                "ACTIVE"));
            }
            return new AuthTokenResponse(
                    AuthTokenResponse.LEGACY_SCHEMA_VERSION,
                    authSessionId,
                    Map.of(
                            AuthTokenResponse.LEGACY_AUDIENCE,
                            new AuthTokenResponse.AccessToken(
                                    "refreshed-access-secret", 900)),
                    "refreshed-refresh-secret",
                    "Bearer",
                    1_209_600,
                    new AuthTokenResponse.User(
                            AUTH_USER_ID,
                            USER_ID,
                            "user@example.com",
                            "User",
                            null,
                            "ACTIVE"));
        }

        @Override
        public void revokeBestEffort(
                UUID authSessionId,
                String tokenType,
                Map<String, String> accessTokens,
                String refreshToken) {
            revokeCalls.incrementAndGet();
            revokedAccessToken = accessTokens.get(AuthTokenResponse.LEGACY_AUDIENCE);
            revokedRefreshToken = refreshToken;
        }
    }
}
