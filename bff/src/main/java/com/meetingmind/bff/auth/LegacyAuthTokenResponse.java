package com.meetingmind.bff.auth;

public record LegacyAuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshExpiresIn,
        LegacyAuthUser user) {

    @Override
    public String toString() {
        return "LegacyAuthTokenResponse[REDACTED]";
    }
}
