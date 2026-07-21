package com.meetingmind.bff.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class BffAuthController {

    private final AuthClient authClient;
    private final BffSessionManager sessionManager;
    private final BffTokenManager tokenManager;
    private final BffAllDeviceLogoutService allDeviceLogoutService;

    public BffAuthController(
            AuthClient authClient,
            BffSessionManager sessionManager,
            BffTokenManager tokenManager,
            BffAllDeviceLogoutService allDeviceLogoutService) {
        this.authClient = authClient;
        this.sessionManager = sessionManager;
        this.tokenManager = tokenManager;
        this.allDeviceLogoutService = allDeviceLogoutService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public BffAuthenticatedResponse signup(
            @Valid @RequestBody BrowserAuthRequests.Signup request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        AuthTokenResponse tokens =
                authClient.signup(request, servletRequest.getHeader("User-Agent"));
        return sessionManager.establish(tokens, request.rememberMe(), servletRequest, servletResponse);
    }

    @PostMapping("/login")
    public BffAuthenticatedResponse login(
            @Valid @RequestBody BrowserAuthRequests.Login request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        AuthTokenResponse tokens =
                authClient.login(request, servletRequest.getHeader("User-Agent"));
        return sessionManager.establish(tokens, request.rememberMe(), servletRequest, servletResponse);
    }

    @PostMapping("/google")
    public BffAuthenticatedResponse google(
            @Valid @RequestBody BrowserAuthRequests.Google request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        AuthTokenResponse tokens =
                authClient.google(request, servletRequest.getHeader("User-Agent"));
        return sessionManager.establish(tokens, request.rememberMe(), servletRequest, servletResponse);
    }

    @GetMapping("/session")
    public ResponseEntity<BffSessionBootstrapResponse> session(
            Authentication authentication, HttpServletRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(sessionManager.currentSession(authentication, request));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        tokenManager.logout(request);
    }

    @PostMapping("/reauthenticate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reauthenticate(
            @Valid @RequestBody BrowserAuthRequests.Reauthenticate request,
            HttpServletRequest servletRequest) {
        allDeviceLogoutService.reauthenticate(request, servletRequest);
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(HttpServletRequest request) {
        allDeviceLogoutService.logoutAll(request);
    }
}
