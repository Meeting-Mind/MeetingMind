package com.meetingmind.demo.auth;

import com.meetingmind.demo.auth.target.AccessTokenValidationException;
import com.meetingmind.demo.auth.target.TargetAccessTokenValidator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/users")
public class CoreUserProjectionController {

    private final AuthStore store;
    private final TargetAccessTokenValidator targetValidator;
    private final Clock clock;

    public CoreUserProjectionController(
            AuthStore store,
            TargetAccessTokenValidator targetValidator,
            Clock coreAuthClock) {
        this.store = store;
        this.targetValidator = targetValidator;
        this.clock = coreAuthClock;
    }

    @PostMapping("/projection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void project(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ProjectionRequest request) {
        UUID subject = validateTargetSubject(authorization);
        if (!subject.equals(request.authUserId())
                || !request.resourceUserId().equals("user-" + request.authUserId())) {
            throw new AuthException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_USER_PROJECTION",
                    "사용자 projection 식별자가 올바르지 않습니다.");
        }
        try {
            store.upsertAuthProjection(
                    request.authUserId(),
                    request.resourceUserId(),
                    request.email(),
                    request.displayName().trim(),
                    request.pictureUrl(),
                    request.status().toLowerCase(java.util.Locale.ROOT),
                    clock.instant());
        } catch (AuthException exception) {
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            throw new AuthException(
                    HttpStatus.CONFLICT,
                    "USER_PROJECTION_CONFLICT",
                    "사용자 projection이 기존 데이터와 충돌합니다.");
        }
    }

    private UUID validateTargetSubject(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw unauthorized();
        }
        try {
            return targetValidator
                    .validate(authorization.substring("Bearer ".length()).trim())
                    .userId();
        } catch (AccessTokenValidationException exception) {
            throw unauthorized();
        }
    }

    private AuthException unauthorized() {
        return new AuthException(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "target access token이 올바르지 않습니다.");
    }

    public record ProjectionRequest(
            @NotNull UUID authUserId,
            @NotBlank
            @Pattern(regexp = "user-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
            String resourceUserId,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 200) String displayName,
            @Size(max = 2048) String pictureUrl,
            @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status) {
    }
}
