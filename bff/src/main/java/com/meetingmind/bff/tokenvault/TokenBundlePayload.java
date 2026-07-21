package com.meetingmind.bff.tokenvault;

import java.time.Instant;
import java.util.Map;
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
        Set<String> scopes,
        Map<String, AccessToken> accessTokens) {

    public TokenBundlePayload(
            UUID authSessionId,
            String accessToken,
            String refreshToken,
            String tokenType,
            Instant accessExpiresAt,
            Instant refreshExpiresAt,
            String issuer,
            Set<String> audiences,
            Set<String> scopes) {
        this(
                authSessionId,
                accessToken,
                refreshToken,
                tokenType,
                accessExpiresAt,
                refreshExpiresAt,
                issuer,
                audiences,
                scopes,
                defaultAccessTokens(accessToken, accessExpiresAt, audiences)
        );
    }

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
        accessTokens = accessTokens == null ? Map.of() : Map.copyOf(accessTokens);
        if (accessTokens.isEmpty()
                || accessTokens.values().stream().anyMatch(token -> token == null
                        || isBlank(token.token())
                        || token.expiresAt() == null)) {
            throw TokenVaultException.of(TokenVaultException.Code.INVALID_BUNDLE);
        }
    }

    public AccessToken accessTokenFor(String audience) {
        AccessToken token = accessTokens.get(audience);
        if (token == null) {
            throw TokenVaultException.of(TokenVaultException.Code.INVALID_BUNDLE);
        }
        return token;
    }

    @Override
    public String toString() {
        return "TokenBundlePayload[REDACTED]";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Map<String, AccessToken> defaultAccessTokens(
            String accessToken,
            Instant accessExpiresAt,
            Set<String> audiences) {
        Set<String> normalizedAudiences = audiences == null ? Set.of() : audiences;
        return normalizedAudiences.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                audience -> audience,
                audience -> new AccessToken(accessToken, accessExpiresAt)
        ));
    }

    public record AccessToken(String token, Instant expiresAt) {
    }
}
