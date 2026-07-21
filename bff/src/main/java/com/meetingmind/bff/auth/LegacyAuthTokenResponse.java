package com.meetingmind.bff.auth;

import com.meetingmind.bff.tokenvault.TokenBundlePayload;
import java.util.Map;
import java.util.UUID;

public record LegacyAuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshExpiresIn,
        LegacyAuthUser user,
        UUID authSessionId,
        Map<String, TokenBundlePayload.AccessToken> accessTokens) {

    public LegacyAuthTokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            long refreshExpiresIn,
            LegacyAuthUser user) {
        this(accessToken, refreshToken, tokenType, expiresIn, refreshExpiresIn, user, null, Map.of());
    }

    public LegacyAuthTokenResponse {
        accessTokens = accessTokens == null ? Map.of() : Map.copyOf(accessTokens);
    }

    @Override
    public String toString() {
        return "LegacyAuthTokenResponse[REDACTED]";
    }
}
