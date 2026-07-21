package com.meetingmind.bff.auth;

import java.util.UUID;
import java.time.Instant;

public interface CompatibilityAuthClient {

    LegacyAuthTokenResponse signup(BrowserAuthRequests.Signup request, String userAgent);

    LegacyAuthTokenResponse login(BrowserAuthRequests.Login request, String userAgent);

    LegacyAuthTokenResponse google(BrowserAuthRequests.Google request, String userAgent);

    LegacyAuthTokenResponse refresh(String refreshToken, String userAgent);

    default LegacyAuthTokenResponse refresh(UUID authSessionId, String refreshToken, String userAgent) {
        return refresh(refreshToken, userAgent);
    }

    void revokeBestEffort(String tokenType, String accessToken, String refreshToken);

    default void revokeBestEffort(
            UUID authSessionId,
            String tokenType,
            String accessToken,
            String refreshToken) {
        revokeBestEffort(tokenType, accessToken, refreshToken);
    }

    default void revokeBestEffort(LegacyAuthTokenResponse tokens) {
        revokeBestEffort(tokens.authSessionId(), tokens.tokenType(), tokens.accessToken(), tokens.refreshToken());
    }

    default void projectUser(LegacyAuthTokenResponse tokens) {
        // Legacy Backend users already exist in Core.
    }

    default void projectProfile(BffAuthUser user, String tokenType, String coreAccessToken) {
        // Legacy Backend owns the same user profile projection.
    }

    default void reauthenticate(
            UUID authSessionId,
            UUID userId,
            String password,
            String googleCredential) {
        throw BffAuthException.of(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "REAUTHENTICATION_UNAVAILABLE",
                "현재 인증 전환에서는 모든 기기 로그아웃을 사용할 수 없습니다.");
    }

    default void revokeAll(UUID authSessionId, UUID userId, Instant authenticatedAt) {
        throw BffAuthException.of(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "REAUTHENTICATION_UNAVAILABLE",
                "현재 인증 전환에서는 모든 기기 로그아웃을 사용할 수 없습니다.");
    }

    default boolean requestPasswordReset(String email, String requestIpPrefix) {
        throw accountOperationUnavailable();
    }

    default void resetPassword(String token, String newPassword) {
        throw accountOperationUnavailable();
    }

    default void changePassword(UUID authSessionId, UUID userId, String currentPassword, String newPassword) {
        throw accountOperationUnavailable();
    }

    default BffAuthUser updateProfile(UUID userId, String displayName) {
        throw accountOperationUnavailable();
    }

    default BffAuthUser updateProfileImage(UUID userId, String contentType, String filename, byte[] bytes) {
        throw accountOperationUnavailable();
    }

    default void prepareWithdrawal(String tokenType, String coreAccessToken) {
        throw accountOperationUnavailable();
    }

    default void completeWithdrawal(String tokenType, String coreAccessToken) {
        throw accountOperationUnavailable();
    }

    default void cancelWithdrawal(String tokenType, String coreAccessToken) {
        throw accountOperationUnavailable();
    }

    default void withdraw(UUID authSessionId, UUID userId, Instant authenticatedAt) {
        throw accountOperationUnavailable();
    }

    private static BffAuthException accountOperationUnavailable() {
        return BffAuthException.of(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "ACCOUNT_MANAGEMENT_UNAVAILABLE",
                "현재 인증 전환에서는 계정 관리 기능을 사용할 수 없습니다.");
    }
}
