package com.meetingmind.bff.auth;

import java.util.Map;
import java.util.UUID;

public record AuthTokenResponse(
        int schemaVersion,
        UUID authSessionId,
        Map<String, AccessToken> accessTokens,
        String refreshToken,
        String tokenType,
        long refreshExpiresIn,
        User user) {

    public static final int LEGACY_SCHEMA_VERSION = 1;
    public static final int TARGET_SCHEMA_VERSION = 2;
    public static final String LEGACY_AUDIENCE = "meetingmind-legacy";

    public AuthTokenResponse {
        accessTokens = accessTokens == null ? Map.of() : Map.copyOf(accessTokens);
    }

    @Override
    public String toString() {
        return "AuthTokenResponse[authSessionId=" + authSessionId + ", tokens=REDACTED]";
    }

    public record AccessToken(String token, long expiresIn) {

        @Override
        public String toString() {
            return "AccessToken[REDACTED]";
        }
    }

    public record User(
            UUID authUserId,
            String resourceUserId,
            String email,
            String displayName,
            String pictureUrl,
            String status) {
    }
}
