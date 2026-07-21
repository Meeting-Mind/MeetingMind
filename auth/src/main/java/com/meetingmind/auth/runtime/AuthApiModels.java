package com.meetingmind.auth.runtime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class AuthApiModels {

    private AuthApiModels() {
    }

    record ClientContext(@Size(max = 256) String deviceLabel) {
    }

    record SignupRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 128) String password,
            @NotBlank @Size(max = 200) String displayName,
            @Valid ClientContext clientContext
    ) {
        @Override
        public String toString() {
            return "SignupRequest[redacted]";
        }
    }

    record LoginRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 128) String password,
            @Valid ClientContext clientContext
    ) {
        @Override
        public String toString() {
            return "LoginRequest[redacted]";
        }
    }

    record GoogleRequest(
            @NotBlank @Size(max = 8192) String credential,
            @Valid ClientContext clientContext
    ) {
        @Override
        public String toString() {
            return "GoogleRequest[redacted]";
        }
    }

    record RefreshRequest(
            @NotNull UUID authSessionId,
            @NotBlank
            @Pattern(regexp = "mmr_[A-Za-z0-9_-]{43}")
            String refreshToken
    ) {
        @Override
        public String toString() {
            return "RefreshRequest[authSessionId=" + authSessionId + ", refreshToken=REDACTED]";
        }
    }

    record RevokeRequest(
            @NotNull UUID authSessionId,
            @NotBlank @Pattern(regexp = "CURRENT_LOGOUT") String reason
    ) {
    }

    record RevokeAllRequest(
            @NotNull UUID currentAuthSessionId,
            @NotNull UUID userId,
            @NotBlank @Pattern(regexp = "ALL_DEVICE_LOGOUT") String reason,
            @NotNull Instant authenticatedAt
    ) {
    }

    record ReauthenticateRequest(
            @NotNull UUID currentAuthSessionId,
            @NotNull UUID userId,
            @Size(max = 128) String password,
            @Size(max = 8192) String googleCredential
    ) {
        @jakarta.validation.constraints.AssertTrue(message = "password 또는 googleCredential 중 하나가 필요합니다.")
        boolean hasExactlyOneCredential() {
            return (password != null && !password.isBlank()) ^ (googleCredential != null && !googleCredential.isBlank());
        }

        @Override
        public String toString() {
            return "ReauthenticateRequest[redacted]";
        }
    }

    record PasswordResetRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @Size(max = 64) String requestIpPrefix
    ) {
    }

    record PasswordResetConfirmRequest(
            @NotBlank @Pattern(regexp = "mmpr_[A-Za-z0-9_-]{43}") String token,
            @NotBlank @Size(max = 128) String newPassword
    ) {
        @Override
        public String toString() {
            return "PasswordResetConfirmRequest[token=REDACTED, newPassword=REDACTED]";
        }
    }

    record PasswordChangeRequest(
            @NotNull UUID currentAuthSessionId,
            @NotNull UUID userId,
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(max = 128) String newPassword
    ) {
        @Override
        public String toString() {
            return "PasswordChangeRequest[redacted]";
        }
    }

    record ProfileUpdateRequest(
            @NotNull UUID userId,
            @NotBlank @Size(max = 200) String displayName
    ) {
    }

    record WithdrawalRequest(
            @NotNull UUID currentAuthSessionId,
            @NotNull UUID userId,
            @NotNull Instant authenticatedAt
    ) {
    }

    record AcceptedResponse(boolean accepted) {
    }

    record TokenResponse(
            List<AccessTokenView> accessTokens,
            String refreshToken,
            String tokenType,
            long refreshExpiresIn,
            UUID authSessionId,
            UserView user
    ) {
        @Override
        public String toString() {
            return "TokenResponse[authSessionId=" + authSessionId + ", tokens=REDACTED]";
        }
    }

    record AccessTokenView(String audience, String token, long expiresIn) {
        @Override
        public String toString() {
            return "AccessTokenView[audience=" + audience + ", token=REDACTED]";
        }
    }

    record UserView(
            UUID id,
            String email,
            String displayName,
            String pictureUrl,
            String status
    ) {
    }

    record ErrorResponse(
            String code,
            String message,
            List<FieldError> fieldErrors,
            String traceId
    ) {
    }

    record FieldError(String field, String message) {
    }
}
