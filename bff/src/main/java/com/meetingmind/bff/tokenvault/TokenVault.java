package com.meetingmind.bff.tokenvault;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.bff.tokenvault.crypto.EncryptedEnvelope;
import com.meetingmind.bff.tokenvault.crypto.TokenEncryptionContext;
import com.meetingmind.bff.tokenvault.crypto.TokenPayloadCipher;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

public final class TokenVault {

    private final EncryptedTokenBundleStore store;
    private final TokenPayloadCipher payloadCipher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TokenVault(
            EncryptedTokenBundleStore store,
            TokenPayloadCipher payloadCipher,
            ObjectMapper objectMapper,
            Clock clock) {
        this.store = store;
        this.payloadCipher = payloadCipher;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public EncryptedTokenBundle create(UUID bundleId, TokenBundlePayload payload) {
        validateNewPayload(payload);
        Instant now = clock.instant();
        EncryptedEnvelope envelope = encrypt(bundleId, payload, 1);
        EncryptedTokenBundle bundle = encryptedBundle(bundleId, payload, envelope, 1, now, now);
        store.create(bundle);
        return bundle;
    }

    public TokenBundlePayload read(UUID bundleId, UUID expectedAuthSessionId) {
        return readVersioned(bundleId, expectedAuthSessionId).payload();
    }

    public VersionedTokenBundle readVersioned(UUID bundleId, UUID expectedAuthSessionId) {
        EncryptedTokenBundle bundle = requireBundle(bundleId);
        requireAuthSession(bundle.authSessionId(), expectedAuthSessionId);
        TokenEncryptionContext context =
                new TokenEncryptionContext(bundle.id(), bundle.authSessionId(), bundle.version());
        byte[] plaintext = payloadCipher.decrypt(
                context,
                new EncryptedEnvelope(
                        bundle.encryptedPayload(), bundle.encryptedDataKey(), bundle.keyId()));
        try {
            TokenBundlePayload payload = objectMapper.readValue(plaintext, TokenBundlePayload.class);
            validateStoredPayload(payload);
            requireAuthSession(bundle.authSessionId(), payload.authSessionId());
            return new VersionedTokenBundle(bundle.version(), payload);
        } catch (IOException | RuntimeException exception) {
            throw TokenVaultException.of(TokenVaultException.Code.CRYPTO_FAILURE);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    public EncryptedTokenBundle rotate(UUID bundleId, long expectedVersion, TokenBundlePayload replacement) {
        validateNewPayload(replacement);
        EncryptedTokenBundle current = requireBundle(bundleId);
        if (current.version() != expectedVersion) {
            throw TokenVaultException.of(TokenVaultException.Code.CONCURRENT_UPDATE);
        }
        requireAuthSession(current.authSessionId(), replacement.authSessionId());

        long nextVersion = expectedVersion + 1;
        EncryptedEnvelope envelope = encrypt(bundleId, replacement, nextVersion);
        EncryptedTokenBundle rotated = encryptedBundle(
                bundleId,
                replacement,
                envelope,
                nextVersion,
                current.createdAt(),
                clock.instant());
        if (!store.replace(expectedVersion, rotated)) {
            throw TokenVaultException.of(TokenVaultException.Code.CONCURRENT_UPDATE);
        }
        return rotated;
    }

    public void delete(UUID bundleId) {
        store.deleteById(bundleId);
    }

    private EncryptedEnvelope encrypt(UUID bundleId, TokenBundlePayload payload, long version) {
        byte[] plaintext = serialize(payload);
        try {
            return payloadCipher.encrypt(
                    new TokenEncryptionContext(bundleId, payload.authSessionId(), version), plaintext);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    private byte[] serialize(TokenBundlePayload payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException exception) {
            throw TokenVaultException.of(TokenVaultException.Code.INVALID_BUNDLE);
        }
    }

    private EncryptedTokenBundle encryptedBundle(
            UUID bundleId,
            TokenBundlePayload payload,
            EncryptedEnvelope envelope,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        return new EncryptedTokenBundle(
                bundleId,
                payload.authSessionId(),
                envelope.encryptedPayload(),
                envelope.encryptedDataKey(),
                envelope.keyId(),
                payload.accessExpiresAt(),
                payload.refreshExpiresAt(),
                payload.issuer(),
                payload.audiences(),
                payload.scopes(),
                version,
                createdAt,
                updatedAt);
    }

    private EncryptedTokenBundle requireBundle(UUID bundleId) {
        return store.findById(bundleId)
                .orElseThrow(() -> TokenVaultException.of(TokenVaultException.Code.BUNDLE_NOT_FOUND));
    }

    private void validateNewPayload(TokenBundlePayload payload) {
        if (payload == null
                || !payload.accessExpiresAt().isAfter(clock.instant())
                || payload.refreshExpiresAt().isBefore(payload.accessExpiresAt())) {
            throw TokenVaultException.of(TokenVaultException.Code.INVALID_BUNDLE);
        }
    }

    private void validateStoredPayload(TokenBundlePayload payload) {
        if (payload == null
                || !payload.refreshExpiresAt().isAfter(clock.instant())
                || payload.refreshExpiresAt().isBefore(payload.accessExpiresAt())) {
            throw TokenVaultException.of(TokenVaultException.Code.INVALID_BUNDLE);
        }
    }

    private void requireAuthSession(UUID actual, UUID expected) {
        if (expected == null || !actual.equals(expected)) {
            throw TokenVaultException.of(TokenVaultException.Code.INVALID_BUNDLE);
        }
    }
}
