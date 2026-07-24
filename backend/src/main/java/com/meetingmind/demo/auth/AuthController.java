package com.meetingmind.demo.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public AuthTokenResponse signup(@Valid @RequestBody SignupRequest request, HttpServletRequest servletRequest) {
        return authService.signup(request, servletRequest.getHeader("User-Agent"));
    }

    @PostMapping("/login")
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return authService.login(request, servletRequest.getHeader("User-Agent"));
    }

    @PostMapping("/google")
    public AuthTokenResponse googleLogin(@Valid @RequestBody GoogleLoginRequest request, HttpServletRequest servletRequest) {
        return authService.googleLogin(request, servletRequest.getHeader("User-Agent"));
    }

    @PostMapping("/refresh")
    public AuthTokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest servletRequest) {
        return authService.refresh(request, servletRequest.getHeader("User-Agent"));
    }

    @GetMapping("/me")
    public AuthUserResponse me(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return authService.currentUser(authorizationHeader);
    }

    @PatchMapping("/profile")
    public AuthUserResponse updateProfile(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return authService.updateProfile(authorizationHeader, request);
    }

    @PostMapping("/logout")
    public LogoutResponse logout(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody LogoutRequest request
    ) {
        return authService.logout(authorizationHeader, request);
    }
}
