package com.meetingmind.demo.auth;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal-only saga boundary. The target token subject is the only account identifier. */
@RestController
@RequestMapping("/internal/v1/core/account-withdrawal")
public class CoreAccountWithdrawalController {

    private final CoreAccountWithdrawalService withdrawalService;
    private final TargetAuthUserResolver targetAuthUserResolver;
    private final CoreInternalWorkloadVerifier workloadVerifier;

    public CoreAccountWithdrawalController(
            ObjectProvider<CoreAccountWithdrawalService> withdrawalServiceProvider,
            ObjectProvider<TargetAuthUserResolver> targetAuthUserResolverProvider,
            CoreInternalWorkloadVerifier workloadVerifier
    ) {
        this.withdrawalService = withdrawalServiceProvider.getIfAvailable();
        this.targetAuthUserResolver = targetAuthUserResolverProvider.getIfAvailable();
        this.workloadVerifier = workloadVerifier;
    }

    @PostMapping("/reservation")
    public ResponseEntity<Void> prepare(@RequestHeader("Authorization") String authorizationHeader) {
        requireService().prepare(subject(authorizationHeader));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/complete")
    public ResponseEntity<Void> complete(@RequestHeader("Authorization") String authorizationHeader) {
        requireService().complete(subject(authorizationHeader));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cancel")
    public ResponseEntity<Void> cancel(@RequestHeader("Authorization") String authorizationHeader) {
        requireService().cancel(subject(authorizationHeader));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reconcile")
    public ResponseEntity<Void> reconcile(
            @Valid @org.springframework.web.bind.annotation.RequestBody ReconciliationRequest request,
            HttpServletRequest servletRequest
    ) {
        workloadVerifier.requireAuthWorkload(servletRequest);
        requireService().completeFromAuthEvent(request.authUserId());
        return ResponseEntity.noContent().build();
    }

    private UUID subject(String authorizationHeader) {
        if (targetAuthUserResolver == null) {
            throw unavailable();
        }
        return targetAuthUserResolver.validate(authorizationHeader).userId();
    }

    private CoreAccountWithdrawalService requireService() {
        if (withdrawalService == null) {
            throw unavailable();
        }
        return withdrawalService;
    }

    private AuthException unavailable() {
        return new AuthException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "ACCOUNT_WITHDRAWAL_UNAVAILABLE",
                "계정 탈퇴 처리가 준비되지 않았습니다.");
    }

    record ReconciliationRequest(@NotNull UUID eventId, @NotNull UUID authUserId) {
    }
}
