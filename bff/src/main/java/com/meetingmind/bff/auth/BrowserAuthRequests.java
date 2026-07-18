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

    public record Reauthenticate(
            @NotBlank @Pattern(regexp = "PASSWORD|GOOGLE") String method,
            @Size(max = 128) String password,
            @Size(max = 16_384) String credential) {}
}
