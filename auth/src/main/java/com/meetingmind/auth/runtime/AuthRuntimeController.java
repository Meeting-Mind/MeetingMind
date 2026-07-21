package com.meetingmind.auth.runtime;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/auth")
class AuthRuntimeController {

    private final AuthRuntimeService service;

    AuthRuntimeController(AuthRuntimeService service) {
        this.service = service;
    }

    @PostMapping("/signup")
    ResponseEntity<AuthApiModels.TokenResponse> signup(
            @Valid @RequestBody AuthApiModels.SignupRequest request,
            HttpServletRequest servletRequest
    ) {
        return tokenResponse(
                service.signup(request, RequestTraceFilter.current(servletRequest)),
                HttpStatus.CREATED
        );
    }

    @PostMapping("/login")
    ResponseEntity<AuthApiModels.TokenResponse> login(
            @Valid @RequestBody AuthApiModels.LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        return tokenResponse(
                service.login(request, RequestTraceFilter.current(servletRequest)),
                HttpStatus.OK
        );
    }

    @PostMapping("/google")
    ResponseEntity<AuthApiModels.TokenResponse> google(
            @Valid @RequestBody AuthApiModels.GoogleRequest request,
            HttpServletRequest servletRequest
    ) {
        return tokenResponse(
                service.google(request, RequestTraceFilter.current(servletRequest)),
                HttpStatus.OK
        );
    }

    @PostMapping("/refresh")
    ResponseEntity<AuthApiModels.TokenResponse> refresh(
            @Valid @RequestBody AuthApiModels.RefreshRequest request,
            HttpServletRequest servletRequest
    ) {
        return tokenResponse(
                service.refresh(request, RequestTraceFilter.current(servletRequest)),
                HttpStatus.OK
        );
    }

    @PostMapping("/revoke")
    ResponseEntity<Void> revoke(
            @Valid @RequestBody AuthApiModels.RevokeRequest request,
            HttpServletRequest servletRequest
    ) {
        service.revoke(request, RequestTraceFilter.current(servletRequest));
        return noContent();
    }

    @PostMapping("/revoke-all")
    ResponseEntity<Void> revokeAll(
            @Valid @RequestBody AuthApiModels.RevokeAllRequest request,
            HttpServletRequest servletRequest
    ) {
        service.revokeAll(request, RequestTraceFilter.current(servletRequest));
        return noContent();
    }

    @PostMapping("/re-authenticate")
    ResponseEntity<Void> reauthenticate(
            @Valid @RequestBody AuthApiModels.ReauthenticateRequest request,
            HttpServletRequest servletRequest
    ) {
        service.reauthenticate(request, RequestTraceFilter.current(servletRequest));
        return noContent();
    }

    @PostMapping("/password-reset-requests")
    ResponseEntity<AuthApiModels.AcceptedResponse> requestPasswordReset(
            @Valid @RequestBody AuthApiModels.PasswordResetRequest request,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(service.requestPasswordReset(request, RequestTraceFilter.current(servletRequest)));
    }

    @PostMapping("/password-resets")
    ResponseEntity<Void> resetPassword(
            @Valid @RequestBody AuthApiModels.PasswordResetConfirmRequest request,
            HttpServletRequest servletRequest
    ) {
        service.resetPassword(request, RequestTraceFilter.current(servletRequest));
        return noContent();
    }

    @PostMapping("/password")
    ResponseEntity<Void> changePassword(
            @Valid @RequestBody AuthApiModels.PasswordChangeRequest request,
            HttpServletRequest servletRequest
    ) {
        service.changePassword(request, RequestTraceFilter.current(servletRequest));
        return noContent();
    }

    @PatchMapping("/profile")
    ResponseEntity<AuthApiModels.UserView> updateProfile(
            @Valid @RequestBody AuthApiModels.ProfileUpdateRequest request,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.updateProfile(request, RequestTraceFilter.current(servletRequest)));
    }

    @PostMapping(path = "/profile-image", consumes = "multipart/form-data")
    ResponseEntity<AuthApiModels.UserView> updateProfileImage(
            @RequestParam UUID userId,
            @RequestPart("image") MultipartFile image,
            HttpServletRequest servletRequest
    ) throws java.io.IOException {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.updateProfileImage(
                        userId,
                        image.getContentType(),
                        image.getBytes(),
                        RequestTraceFilter.current(servletRequest)));
    }

    @PostMapping("/withdrawal")
    ResponseEntity<Void> withdrawal(
            @Valid @RequestBody AuthApiModels.WithdrawalRequest request,
            HttpServletRequest servletRequest
    ) {
        service.withdraw(request, RequestTraceFilter.current(servletRequest));
        return noContent();
    }

    private ResponseEntity<AuthApiModels.TokenResponse> tokenResponse(
            AuthApiModels.TokenResponse response,
            HttpStatus status
    ) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(response);
    }

    private ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
