package com.meetingmind.bff.tokenvault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.bff.tokenvault.crypto.AesGcmTokenPayloadCipher;
import com.meetingmind.bff.tokenvault.crypto.EncryptedEnvelope;
import com.meetingmind.bff.tokenvault.crypto.LocalEnvelopeKeyService;
import com.meetingmind.bff.tokenvault.crypto.TokenEncryptionContext;
import com.meetingmind.bff.tokenvault.crypto.TokenPayloadCipher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TokenVaultTest {

    private static final Instant NOW = Instant.parse("2026-07-16T00:00:00Z");
    private static final String LOCAL_KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final InMemoryStore store = new InMemoryStore();
    private final TokenPayloadCipher cipher = new AesGcmTokenPayloadCipher(
            new LocalEnvelopeKeyService("local-key-v1", LOCAL_KEY));
    private final TokenVault tokenVault = new TokenVault(store, cipher, objectMapper, clock);

    @Test
    void storesOnlyCiphertextAndReadsTheOriginalPayload() throws Exception {
        UUID bundleId = UUID.randomUUID();
        TokenBundlePayload payload = payload("access-secret-v1", "refresh-secret-v1");

        EncryptedTokenBundle encrypted = tokenVault.create(bundleId, payload);
        String persisted = objectMapper.writeValueAsString(encrypted);

        assertThat(persisted)
                .doesNotContain(payload.accessToken())
                .doesNotContain(payload.refreshToken());
        assertThat(payload.toString()).isEqualTo("TokenBundlePayload[REDACTED]");
        assertThat(tokenVault.read(bundleId, payload.authSessionId())).isEqualTo(payload);
    }

    @Test
    void rejectsCiphertextMovedToAnotherVersion() {
        UUID bundleId = UUID.randomUUID();
        TokenBundlePayload payload = payload("access-secret-v1", "refresh-secret-v1");
        EncryptedTokenBundle original = tokenVault.create(bundleId, payload);
        store.bundle = copyWithVersion(original, 2);

        assertThatThrownBy(() -> tokenVault.read(bundleId, payload.authSessionId()))
                .isInstanceOfSatisfying(TokenVaultException.class, exception ->
                        assertThat(exception.code()).isEqualTo(TokenVaultException.Code.CRYPTO_FAILURE));
    }

    @Test
    void rejectsARequestFromAnotherAuthenticationSessionBeforeDecrypting() {
        UUID bundleId = UUID.randomUUID();
        TokenBundlePayload payload = payload("access-secret-v1", "refresh-secret-v1");
        tokenVault.create(bundleId, payload);

        assertThatThrownBy(() -> tokenVault.read(bundleId, UUID.randomUUID()))
                .isInstanceOfSatisfying(TokenVaultException.class, exception ->
                        assertThat(exception.code()).isEqualTo(TokenVaultException.Code.INVALID_BUNDLE));
    }

    @Test
    void leavesCurrentBundleUntouchedWhenRotationEncryptionFails() {
        UUID bundleId = UUID.randomUUID();
        TokenBundlePayload currentPayload = payload("access-secret-v1", "refresh-secret-v1");
        FailingCipher failingCipher = new FailingCipher(cipher);
        TokenVault vault = new TokenVault(store, failingCipher, objectMapper, clock);
        EncryptedTokenBundle current = vault.create(bundleId, currentPayload);
        failingCipher.fail = true;

        assertThatThrownBy(() -> vault.rotate(
                        bundleId,
                        current.version(),
                        replacement(currentPayload, "access-secret-v2", "refresh-secret-v2")))
                .isInstanceOfSatisfying(TokenVaultException.class, exception ->
                        assertThat(exception.code()).isEqualTo(TokenVaultException.Code.CRYPTO_FAILURE));
        assertThat(store.bundle).isEqualTo(current);
    }

    @Test
    void leavesCurrentBundleUntouchedWhenAtomicReplaceLosesTheRace() {
        UUID bundleId = UUID.randomUUID();
        TokenBundlePayload currentPayload = payload("access-secret-v1", "refresh-secret-v1");
        EncryptedTokenBundle current = tokenVault.create(bundleId, currentPayload);
        store.rejectReplace = true;

        assertThatThrownBy(() -> tokenVault.rotate(
                        bundleId,
                        current.version(),
                        replacement(currentPayload, "access-secret-v2", "refresh-secret-v2")))
                .isInstanceOfSatisfying(TokenVaultException.class, exception ->
                        assertThat(exception.code()).isEqualTo(TokenVaultException.Code.CONCURRENT_UPDATE));
        assertThat(store.bundle).isEqualTo(current);
    }

    @Test
    void rejectsMissingOrWrongLocalMasterKey() {
        assertThatThrownBy(() -> new LocalEnvelopeKeyService("key", ""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new LocalEnvelopeKeyService("key", "dG9vLXNob3J0"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void readsAnExpiredAccessTokenWhileRefreshIsStillUsable() {
        UUID bundleId = UUID.randomUUID();
        TokenBundlePayload payload = new TokenBundlePayload(
                UUID.randomUUID(),
                "expired-access-secret",
                "usable-refresh-secret",
                "Bearer",
                NOW.plusSeconds(1),
                NOW.plusSeconds(1209600),
                "meetingmind-auth",
                Set.of("meetingmind-core"),
                Set.of());
        TokenVault writer = new TokenVault(store, cipher, objectMapper, Clock.fixed(NOW, ZoneOffset.UTC));
        writer.create(bundleId, payload);
        TokenVault reader = new TokenVault(
                store,
                cipher,
                objectMapper,
                Clock.fixed(NOW.plusSeconds(2), ZoneOffset.UTC));

        assertThat(reader.readVersioned(bundleId, payload.authSessionId()).payload()).isEqualTo(payload);
    }

    private TokenBundlePayload payload(String accessToken, String refreshToken) {
        return new TokenBundlePayload(
                UUID.randomUUID(),
                accessToken,
                refreshToken,
                "Bearer",
                NOW.plusSeconds(900),
                NOW.plusSeconds(1209600),
                "meetingmind-auth",
                Set.of("meetingmind-core"),
                Set.of("meeting:read"));
    }

    private TokenBundlePayload replacement(
            TokenBundlePayload current, String accessToken, String refreshToken) {
        return new TokenBundlePayload(
                current.authSessionId(),
                accessToken,
                refreshToken,
                current.tokenType(),
                NOW.plusSeconds(1800),
                current.refreshExpiresAt(),
                current.issuer(),
                current.audiences(),
                current.scopes());
    }

    private EncryptedTokenBundle copyWithVersion(EncryptedTokenBundle bundle, long version) {
        return new EncryptedTokenBundle(
                bundle.id(),
                bundle.authSessionId(),
                bundle.encryptedPayload(),
                bundle.encryptedDataKey(),
                bundle.keyId(),
                bundle.accessExpiresAt(),
                bundle.refreshExpiresAt(),
                bundle.issuer(),
                bundle.audiences(),
                bundle.scopes(),
                version,
                bundle.createdAt(),
                bundle.updatedAt());
    }

    private static final class InMemoryStore implements EncryptedTokenBundleStore {

        private EncryptedTokenBundle bundle;
        private boolean rejectReplace;

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
            if (rejectReplace || bundle == null || bundle.version() != expectedVersion) {
                return false;
            }
            bundle = replacement;
            return true;
        }

        @Override
        public void deleteById(UUID bundleId) {
            if (bundle != null && bundle.id().equals(bundleId)) {
                bundle = null;
            }
        }
    }

    private static final class FailingCipher implements TokenPayloadCipher {

        private final TokenPayloadCipher delegate;
        private boolean fail;

        private FailingCipher(TokenPayloadCipher delegate) {
            this.delegate = delegate;
        }

        @Override
        public EncryptedEnvelope encrypt(TokenEncryptionContext context, byte[] plaintext) {
            if (fail) {
                throw TokenVaultException.of(TokenVaultException.Code.CRYPTO_FAILURE);
            }
            return delegate.encrypt(context, plaintext);
        }

        @Override
        public byte[] decrypt(TokenEncryptionContext context, EncryptedEnvelope envelope) {
            return delegate.decrypt(context, envelope);
        }
    }
}
