package com.meetingmind.bff.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/auth")
public class BffAuthController {

    private final CompatibilityAuthClient compatibilityAuthClient;
    private final BffSessionManager sessionManager;
    private final BffTokenManager tokenManager;

    public BffAuthController(
            CompatibilityAuthClient compatibilityAuthClient,
            BffSessionManager sessionManager,
            BffTokenManager tokenManager) {
        this.compatibilityAuthClient = compatibilityAuthClient;
        this.sessionManager = sessionManager;
        this.tokenManager = tokenManager;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public BffAuthenticatedResponse signup(
            @Valid @RequestBody BrowserAuthRequests.Signup request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        LegacyAuthTokenResponse tokens =
                compatibilityAuthClient.signup(request, servletRequest.getHeader("User-Agent"));
        return sessionManager.establish(tokens, request.rememberMe(), servletRequest, servletResponse);
    }

    @PostMapping("/login")
    public BffAuthenticatedResponse login(
            @Valid @RequestBody BrowserAuthRequests.Login request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        LegacyAuthTokenResponse tokens =
                compatibilityAuthClient.login(request, servletRequest.getHeader("User-Agent"));
        return sessionManager.establish(tokens, request.rememberMe(), servletRequest, servletResponse);
    }

    @PostMapping("/google")
    public BffAuthenticatedResponse google(
            @Valid @RequestBody BrowserAuthRequests.Google request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        LegacyAuthTokenResponse tokens =
                compatibilityAuthClient.google(request, servletRequest.getHeader("User-Agent"));
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

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(
            @RequestBody(required = false) BrowserAuthRequests.LogoutAll request,
            HttpServletRequest servletRequest) {
        tokenManager.logoutAll(servletRequest, request);
    }

    @PostMapping("/password-reset-requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public java.util.Map<String, Boolean> requestPasswordReset(
            @Valid @RequestBody BrowserAuthRequests.PasswordResetRequest request,
            HttpServletRequest servletRequest) {
        return java.util.Map.of("accepted", tokenManager.requestPasswordReset(request, remoteIpPrefix(servletRequest)));
    }

    @PostMapping("/password-resets")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody BrowserAuthRequests.PasswordResetConfirm request) {
        tokenManager.resetPassword(request);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @Valid @RequestBody BrowserAuthRequests.PasswordChange request,
            HttpServletRequest servletRequest) {
        tokenManager.changePassword(servletRequest, request);
    }

    @org.springframework.web.bind.annotation.PatchMapping("/profile")
    public BffAuthUser updateProfile(
            @Valid @RequestBody BrowserAuthRequests.ProfileUpdate request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        BffAuthUser user = tokenManager.updateProfile(servletRequest, request);
        sessionManager.updateCurrentUser(user, servletRequest, servletResponse);
        return user;
    }

    @PostMapping(path = "/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BffAuthUser updateProfileImage(
            @RequestPart("image") MultipartFile image,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        BffAuthUser user = tokenManager.updateProfileImage(servletRequest, ProfileImageUploadValidator.validate(image));
        sessionManager.updateCurrentUser(user, servletRequest, servletResponse);
        return user;
    }

    @PostMapping("/withdrawal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdrawal(
            @Valid @RequestBody BrowserAuthRequests.Withdrawal request,
            HttpServletRequest servletRequest) {
        tokenManager.withdraw(servletRequest, request);
    }

    private String remoteIpPrefix(HttpServletRequest request) {
        try {
            byte[] address = InetAddress.getByName(request.getRemoteAddr()).getAddress();
            if (address.length == 4) {
                return "%d.%d.%d.0/24".formatted(
                        Byte.toUnsignedInt(address[0]), Byte.toUnsignedInt(address[1]), Byte.toUnsignedInt(address[2]));
            }
            return "ipv6-" + HexFormat.of().formatHex(address, 0, 8) + "/64";
        } catch (UnknownHostException | RuntimeException exception) {
            return null;
        }
    }
}
