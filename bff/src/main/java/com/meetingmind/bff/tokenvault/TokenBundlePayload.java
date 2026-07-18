package com.meetingmind.bff.tokenvault;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record TokenBundlePayload(
        UUID authSessionId,
        String accessToken,
        String refreshToken,
        String tokenType,
        Instant accessExpiresAt,
        Instant refreshExpiresAt,
        String issuer,
        Set<String> audiences,
        Set<String> scopes) {

    public TokenBundlePayload {
        if (authSessionId == null
                || isBlank(accessToken)
                || isBlank(refreshToken)
                || isBlank(tokenType)
                || accessExpiresAt == null
                || refreshExpiresAt == null
                || isBlank(issuer)) {
            throw TokenVaultException.of(TokenVaultException.Code.INVALID_BUNDLE);
        }
        audiences = audiences == null ? Set.of() : Set.copyOf(audiences);
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }

    @Override
    public String toString() {
        return "TokenBundlePayload[REDACTED]";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
