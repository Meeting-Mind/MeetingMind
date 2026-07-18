package com.meetingmind.bff.tokenvault;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record EncryptedTokenBundle(
        UUID id,
        UUID authSessionId,
        byte[] encryptedPayload,
        byte[] encryptedDataKey,
        String keyId,
        int schemaVersion,
        Map<String, Instant> accessExpiresAtByAudience,
        Instant refreshExpiresAt,
        String issuer,
        Set<String> audiences,
        Map<String, Set<String>> scopesByAudience,
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
                || (schemaVersion != 1 && schemaVersion != 2)
                || accessExpiresAtByAudience == null
                || accessExpiresAtByAudience.isEmpty()
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
        accessExpiresAtByAudience = Map.copyOf(accessExpiresAtByAudience);
        audiences = audiences == null ? Set.of() : Set.copyOf(audiences);
        scopesByAudience = scopesByAudience == null
                ? Map.of()
                : scopesByAudience.entrySet().stream()
                        .collect(java.util.stream.Collectors.toUnmodifiableMap(
                                Map.Entry::getKey,
                                entry -> entry.getValue() == null
                                        ? Set.of()
                                        : Set.copyOf(entry.getValue())));
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
