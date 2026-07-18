package com.meetingmind.demo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meetingmind.demo.auth.target.TargetAccessTokenValidator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CoreUserProjectionControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-18T00:00:00Z");
    private final AuthStore store = mock(AuthStore.class);
    private final TargetAccessTokenValidator validator =
            mock(TargetAccessTokenValidator.class);
    private final CoreUserProjectionController controller =
            new CoreUserProjectionController(
                    store, validator, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void validatesTargetSubjectAndDelegatesAnIdempotentProjection() {
        UUID authUserId = UUID.randomUUID();
        String resourceUserId = "user-" + authUserId;
        when(validator.validate("target-access")).thenReturn(principal(authUserId));
        CoreUserProjectionController.ProjectionRequest request =
                new CoreUserProjectionController.ProjectionRequest(
                        authUserId,
                        resourceUserId,
                        "USER@Example.com",
                        " User ",
                        null,
                        "ACTIVE");

        controller.project("Bearer target-access", request);
        controller.project("Bearer target-access", request);

        verify(store, times(2)).upsertAuthProjection(
                authUserId,
                resourceUserId,
                "USER@Example.com",
                "User",
                null,
                "active",
                NOW);
    }

    @Test
    void rejectsSubjectAndDeterministicResourceIdMismatchBeforeStorage() {
        UUID authUserId = UUID.randomUUID();
        when(validator.validate("target-access")).thenReturn(principal(UUID.randomUUID()));
        CoreUserProjectionController.ProjectionRequest request =
                new CoreUserProjectionController.ProjectionRequest(
                        authUserId,
                        "user-" + authUserId,
                        "user@example.com",
                        "User",
                        null,
                        "ACTIVE");

        assertThatThrownBy(() -> controller.project("Bearer target-access", request))
                .isInstanceOfSatisfying(AuthException.class, exception -> {
                    assertThat(exception.status())
                            .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
                    assertThat(exception.code()).isEqualTo("INVALID_USER_PROJECTION");
                });
        verifyNoInteractions(store);
    }

    private TargetAccessTokenValidator.Principal principal(UUID authUserId) {
        return new TargetAccessTokenValidator.Principal(
                authUserId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                NOW,
                NOW.plusSeconds(600));
    }
}
