package com.meetingmind.demo.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Called by the BFF after a target Auth login, before proxying Core business requests. */
@RestController
@RequestMapping("/internal/v1/core/auth-users")
public class CoreAuthProjectionController {

    private final CoreAuthUserProjectionService projectionService;
    private final TargetAuthUserResolver targetAuthUserResolver;

    public CoreAuthProjectionController(
            CoreAuthUserProjectionService projectionService,
            ObjectProvider<TargetAuthUserResolver> targetAuthUserResolverProvider
    ) {
        this.projectionService = projectionService;
        this.targetAuthUserResolver = targetAuthUserResolverProvider.getIfAvailable();
    }

    @PostMapping("/projection")
    public ResponseEntity<AuthUserResponse> provision(
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody ProjectionRequest request
    ) {
        if (targetAuthUserResolver == null) {
            throw new AuthException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AUTH_USER_MAPPING_UNAVAILABLE",
                    "인증 전환이 준비되지 않았습니다."
            );
        }
        UUID authUserId = targetAuthUserResolver.validate(authorizationHeader).userId();
        AuthUserResponse user = projectionService.provision(
                authUserId,
                new CoreAuthUserProjectionService.ProjectionRequest(
                        request.email(), request.displayName(), request.pictureUrl()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(user);
    }

    public record ProjectionRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 100) String displayName,
            @Size(max = 2_048) String pictureUrl
    ) {
    }
}
