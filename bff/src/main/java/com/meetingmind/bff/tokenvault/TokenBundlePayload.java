package com.meetingmind.bff.tokenvault;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record TokenBundlePayload(
        UUID authSessionId,
        int schemaVersion,
        Map<String, AudienceAccessToken> accessTokens,
        String refreshToken,
        String tokenType,
        Instant refreshExpiresAt,
        String issuer,
        Map<String, Set<String>> scopesByAudience) {

    public TokenBundlePayload {
        if (authSessionId == null
                || (schemaVersion != 1 && schemaVersion != 2)
                || accessTokens == null
                || accessTokens.isEmpty()
                || isBlank(refreshToken)
                || isBlank(tokenType)
                || refreshExpiresAt == null
                || isBlank(issuer)) {
            throw TokenVaultException.of(TokenVaultException.Code.INVALID_BUNDLE);
        }
        accessTokens = Map.copyOf(accessTokens);
        if ((schemaVersion == 1
                        && !accessTokens.keySet().equals(Set.of("meetingmind-legacy")))
                || (schemaVersion == 2
                        && !accessTokens.keySet().equals(Set.of(
                                "meetingmind-core",
                                "meetingmind-ai",
                                "meetingmind-livekit")))) {
            throw TokenVaultException.of(TokenVaultException.Code.INVALID_BUNDLE);
        }
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
    public String toString() {
        return "TokenBundlePayload[REDACTED]";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public AudienceAccessToken requireAccessToken(String audience) {
        AudienceAccessToken token = accessTokens.get(audience);
        if (token == null) {
            throw TokenVaultException.of(TokenVaultException.Code.INVALID_BUNDLE);
        }
        return token;
    }
}
