package com.meetingmind.demo.auth;

public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshExpiresIn,
        AuthUserResponse user
) {
}
