package com.meetingmind.bff.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.bff.config.BffSessionLifetimePolicy;
import com.meetingmind.bff.config.SessionCookieConfiguration;
import com.meetingmind.bff.tokenvault.EncryptedTokenBundle;
import com.meetingmind.bff.tokenvault.EncryptedTokenBundleStore;
import com.meetingmind.bff.tokenvault.TokenBundlePayload;
import com.meetingmind.bff.tokenvault.TokenVault;
import com.meetingmind.bff.tokenvault.TokenVaultException;
import com.meetingmind.bff.tokenvault.crypto.AesGcmTokenPayloadCipher;
import com.meetingmind.bff.tokenvault.crypto.LocalEnvelopeKeyService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

class BffSessionManagerTest {

    private static final Instant NOW = Instant.parse("2026-07-16T00:00:00Z");
    private static final String LOCAL_KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final InMemoryStore store = new InMemoryStore();
    private final TokenVault tokenVault = new TokenVault(
            store,
            new AesGcmTokenPayloadCipher(new LocalEnvelopeKeyService("test-key", LOCAL_KEY)),
            objectMapper,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final BffSessionManager manager = new BffSessionManager(
            tokenVault,
            new NoOpCompatibilityClient(),
            new ChangeSessionIdAuthenticationStrategy(),
            new HttpSessionSecurityContextRepository(),
            new BffSessionLifetimePolicy(
                    Duration.ofMinutes(60),
                    Duration.ofHours(12),
                    Duration.ofDays(7),
                    Duration.ofDays(14)),
            Clock.fixed(NOW, ZoneOffset.UTC),
            "meetingmind-core-legacy",
            "meetingmind-core");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void convertsLegacyTokensIntoAStandardServerSessionWithoutResponseExposure() throws Exception {
        MockHttpServletRequest request = requestWithAnonymousSession();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String previousSessionId = request.getSession().getId();

        BffAuthenticatedResponse authenticated = manager.establish(tokens(), false, request, response);
        MockHttpSession session = (MockHttpSession) request.getSession(false);
        UUID authSessionId = (UUID) session.getAttribute(BffSessionAttributes.AUTH_SESSION_ID);
        UUID tokenBundleId = (UUID) session.getAttribute(BffSessionAttributes.TOKEN_BUNDLE_ID);
        TokenBundlePayload storedTokens = tokenVault.read(tokenBundleId, authSessionId);

        assertThat(session.getId()).isNotEqualTo(previousSessionId);
        assertThat(session.getMaxInactiveInterval()).isEqualTo(3_600);
        assertThat(session.getAttribute(BffSessionAttributes.ABSOLUTE_EXPIRES_AT))
                .isEqualTo(NOW.plus(Duration.ofHours(12)));
        assertThat(request.getAttribute(SessionCookieConfiguration.COOKIE_MAX_AGE_REQUEST_ATTRIBUTE))
                .isNull();
        assertThat(storedTokens.accessToken()).isEqualTo("legacy-access-secret");
        assertThat(storedTokens.accessTokenFor("meetingmind-livekit").token())
                .isEqualTo("legacy-access-secret");
        assertThat(storedTokens.refreshToken()).isEqualTo("legacy-refresh-secret");
        assertThat(objectMapper.writeValueAsString(authenticated))
                .doesNotContain("legacy-access-secret")
                .doesNotContain("legacy-refresh-secret")
                .doesNotContain("accessToken")
                .doesNotContain("refreshToken");
    }

    @Test
    void appliesSevenDayIdleAndFourteenDayPersistentRememberMePolicy() {
        MockHttpServletRequest request = requestWithAnonymousSession();
        manager.establish(tokens(), true, request, new MockHttpServletResponse());

        assertThat(request.getSession().getMaxInactiveInterval()).isEqualTo(604_800);
        assertThat(request.getAttribute(SessionCookieConfiguration.COOKIE_MAX_AGE_REQUEST_ATTRIBUTE))
                .isEqualTo(1_209_600);
        assertThat(request.getSession().getAttribute(BffSessionAttributes.ABSOLUTE_EXPIRES_AT))
                .isEqualTo(NOW.plus(Duration.ofDays(14)));
    }

    @Test
    void bootstrapsOnlyWhenPrincipalAndSessionReferencesMatch() {
        MockHttpServletRequest request = requestWithAnonymousSession();
        manager.establish(tokens(), false, request, new MockHttpServletResponse());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        BffSessionBootstrapResponse bootstrap = manager.currentSession(authentication, request);

        assertThat(bootstrap.authenticated()).isTrue();
        assertThat(bootstrap.user().id()).isEqualTo("user-id");
        assertThat(bootstrap.session().expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(12)));
    }

    @Test
    void invalidatesEveryIndexedSessionAndItsTokenBundleForAnAccountWideRevocation() {
        UUID authSessionId = UUID.randomUUID();
        UUID tokenBundleId = UUID.randomUUID();
        tokenVault.create(tokenBundleId, new TokenBundlePayload(
                authSessionId,
                "access-secret",
                "refresh-secret",
                "Bearer",
                NOW.plus(Duration.ofMinutes(10)),
                NOW.plus(Duration.ofDays(14)),
                "meetingmind-auth",
                java.util.Set.of("meetingmind-core"),
                java.util.Set.of()));
        MapSession browserSession = new MapSession();
        browserSession.setAttribute(BffSessionAttributes.TOKEN_BUNDLE_ID, tokenBundleId);
        FindByIndexNameSessionRepository<Session> indexed = indexedRepository(browserSession);
        BffSessionManager indexedManager = manager(indexed);

        indexedManager.invalidateUserSessions("user-id");

        verify(indexed).deleteById(browserSession.getId());
        assertThatThrownBy(() -> tokenVault.read(tokenBundleId, authSessionId))
                .isInstanceOf(TokenVaultException.class);
    }

    @SuppressWarnings("unchecked")
    private FindByIndexNameSessionRepository<Session> indexedRepository(Session session) {
        FindByIndexNameSessionRepository<Session> repository = mock(FindByIndexNameSessionRepository.class);
        when(repository.findByIndexNameAndIndexValue(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                "user-id")).thenReturn(Map.of(session.getId(), session));
        return repository;
    }

    private BffSessionManager manager(SessionRepository<Session> sessionRepository) {
        return new BffSessionManager(
                tokenVault,
                new NoOpCompatibilityClient(),
                new ChangeSessionIdAuthenticationStrategy(),
                new HttpSessionSecurityContextRepository(),
                new BffSessionLifetimePolicy(
                        Duration.ofMinutes(60),
                        Duration.ofHours(12),
                        Duration.ofDays(7),
                        Duration.ofDays(14)),
                Clock.fixed(NOW, ZoneOffset.UTC),
                "meetingmind-core-legacy",
                "meetingmind-core",
                sessionRepository);
    }

    private MockHttpServletRequest requestWithAnonymousSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        return request;
    }

    private LegacyAuthTokenResponse tokens() {
        return new LegacyAuthTokenResponse(
                "legacy-access-secret",
                "legacy-refresh-secret",
                "Bearer",
                3_600,
                1_209_600,
                new LegacyAuthUser("user-id", "user@example.com", "User", null, "ACTIVE"));
    }

    private static final class InMemoryStore implements EncryptedTokenBundleStore {

        private EncryptedTokenBundle bundle;

        @Override
        public void create(EncryptedTokenBundle bundle) {
            if (this.bundle != null) {
                throw TokenVaultException.of(TokenVaultException.Code.BUNDLE_ALREADY_EXISTS);
            }
            this.bundle = bundle;
        }

        @Override
        public Optional<EncryptedTokenBundle> findById(UUID bundleId) {
            return bundle != null && bundle.id().equals(bundleId) ? Optional.of(bundle) : Optional.empty();
        }

        @Override
        public boolean replace(long expectedVersion, EncryptedTokenBundle replacement) {
            return false;
        }

        @Override
        public void deleteById(UUID bundleId) {
            if (bundle != null && bundle.id().equals(bundleId)) {
                bundle = null;
            }
        }
    }

    private static final class NoOpCompatibilityClient implements CompatibilityAuthClient {

        @Override
        public LegacyAuthTokenResponse signup(BrowserAuthRequests.Signup request, String userAgent) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LegacyAuthTokenResponse login(BrowserAuthRequests.Login request, String userAgent) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LegacyAuthTokenResponse google(BrowserAuthRequests.Google request, String userAgent) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LegacyAuthTokenResponse refresh(String refreshToken, String userAgent) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void revokeBestEffort(String tokenType, String accessToken, String refreshToken) {}
    }
}
