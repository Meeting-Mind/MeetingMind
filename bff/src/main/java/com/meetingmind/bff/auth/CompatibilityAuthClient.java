package com.meetingmind.bff.auth;

public interface CompatibilityAuthClient {

    LegacyAuthTokenResponse signup(BrowserAuthRequests.Signup request, String userAgent);

    LegacyAuthTokenResponse login(BrowserAuthRequests.Login request, String userAgent);

    LegacyAuthTokenResponse google(BrowserAuthRequests.Google request, String userAgent);

    LegacyAuthTokenResponse refresh(String refreshToken, String userAgent);

    void revokeBestEffort(String tokenType, String accessToken, String refreshToken);

    default void revokeBestEffort(LegacyAuthTokenResponse tokens) {
        revokeBestEffort(tokens.tokenType(), tokens.accessToken(), tokens.refreshToken());
    }
}
