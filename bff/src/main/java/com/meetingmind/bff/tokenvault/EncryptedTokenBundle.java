package com.meetingmind.bff.tokenvault;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record EncryptedTokenBundle(
        UUID id,
        UUID authSessionId,
        byte[] encryptedPayload,
        byte[] encryptedDataKey,
        String keyId,
        Instant accessExpiresAt,
        Instant refreshExpiresAt,
        String issuer,
        Set<String> audiences,
        Set<String> scopes,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public EncryptedTokenBundle {
        if (id == null
                || authSessionId == null
                || encryptedPayload == null
                || encryptedPayload.length == 0
                || encryptedDataKey == null
                || encryptedDataKey.length == 0
                || keyId == null
                || keyId.isBlank()
                || accessExpiresAt == null
                || refreshExpiresAt == null
                || issuer == null
                || issuer.isBlank()
                || version < 1
                || createdAt == null
                || updatedAt == null) {
            throw TokenVaultException.of(TokenVaultException.Code.INVALID_BUNDLE);
        }
        encryptedPayload = encryptedPayload.clone();
        encryptedDataKey = encryptedDataKey.clone();
        audiences = audiences == null ? Set.of() : Set.copyOf(audiences);
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }

    @Override
    public byte[] encryptedPayload() {
        return encryptedPayload.clone();
    }

    @Override
    public byte[] encryptedDataKey() {
        return encryptedDataKey.clone();
    }
}
