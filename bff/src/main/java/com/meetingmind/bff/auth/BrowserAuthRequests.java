package com.meetingmind.bff.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class BrowserAuthRequests {

    private BrowserAuthRequests() {}

    public record Signup(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank @Size(max = 100) String displayName,
            boolean rememberMe) {}

    public record Login(
            @Email @NotBlank String email,
            @NotBlank @Size(max = 128) String password,
            boolean rememberMe) {}

    public record Google(
            @NotBlank @Size(max = 16_384) String credential,
            boolean rememberMe) {}

    public record LogoutAll(
            @Size(max = 128) String password,
            @Size(max = 16_384) String googleCredential) {}

    public record PasswordResetRequest(@Email @NotBlank @Size(max = 320) String email) {}

    public record PasswordResetConfirm(
            @NotBlank @Size(max = 128) String token,
            @NotBlank @Size(max = 128) String newPassword) {}

    public record PasswordChange(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(max = 128) String newPassword) {}

    public record ProfileUpdate(@NotBlank @Size(max = 100) String displayName) {}

    public record Withdrawal(
            @NotBlank @Pattern(regexp = "DELETE") String confirmation,
            @Size(max = 128) String password,
            @Size(max = 16_384) String googleCredential) {}
}
