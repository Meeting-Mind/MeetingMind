package com.meetingmind.bff.auth;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public interface AuthClient {

    AuthTokenResponse signup(BrowserAuthRequests.Signup request, String userAgent);

    AuthTokenResponse login(BrowserAuthRequests.Login request, String userAgent);

    AuthTokenResponse google(BrowserAuthRequests.Google request, String userAgent);

    AuthTokenResponse refresh(UUID authSessionId, String refreshToken, String userAgent);

    default Instant reauthenticate(
            UUID currentAuthSessionId,
            UUID userId,
            BrowserAuthRequests.Reauthenticate request) {
        throw allDeviceLogoutUnavailable();
    }

    default void revokeAll(
            UUID currentAuthSessionId,
            UUID userId,
            Instant authenticatedAt) {
        throw allDeviceLogoutUnavailable();
    }

    void revokeBestEffort(
            UUID authSessionId,
            String tokenType,
            Map<String, String> accessTokens,
            String refreshToken);

    default void revokeBestEffort(AuthTokenResponse tokens) {
        revokeBestEffort(
                tokens.authSessionId(),
                tokens.tokenType(),
                tokens.accessTokens().entrySet().stream()
                        .collect(java.util.stream.Collectors.toUnmodifiableMap(
                                Map.Entry::getKey,
                                entry -> entry.getValue().token())),
                tokens.refreshToken());
    }

    private static BffAuthException allDeviceLogoutUnavailable() {
        return BffAuthException.of(
                HttpStatus.CONFLICT,
                "AUTH_FEATURE_UNAVAILABLE",
                "현재 인증 모드에서는 모든 기기 로그아웃을 사용할 수 없습니다.");
    }
}
