package com.meetingmind.demo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.demo.auth.target.AccessTokenValidationException;
import com.meetingmind.demo.auth.target.TargetAccessTokenValidator;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessTokenSubjectResolverTest {

    private final AuthTokenService legacyValidator = mock(AuthTokenService.class);
    private final TargetAccessTokenValidator targetValidator =
            mock(TargetAccessTokenValidator.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void dualModeSelectsExactlyTheTargetValidatorForTheTargetProfile() {
        UUID authUserId = UUID.randomUUID();
        String token = token("""
                {"alg":"RS256","typ":"at+jwt","kid":"key-1"}
                """);
        when(targetValidator.validate(token)).thenReturn(new TargetAccessTokenValidator.Principal(
                authUserId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-07-18T00:00:00Z"),
                Instant.parse("2026-07-18T00:10:00Z")));
        AccessTokenSubjectResolver resolver = resolver(AccessTokenSubjectResolver.Mode.DUAL);

        AccessTokenSubjectResolver.Subject subject = resolver.resolve("Bearer " + token);

        assertThat(subject.authUserId()).isEqualTo(authUserId);
        assertThat(subject.resourceUserId()).isNull();
        verify(targetValidator).validate(token);
        verifyNoInteractions(legacyValidator);
    }

    @Test
    void targetFailureNeverFallsBackToLegacyValidation() {
        String token = token("""
                {"alg":"RS256","typ":"at+jwt","kid":"key-1"}
                """);
        when(targetValidator.validate(token))
                .thenThrow(new AccessTokenValidationException("invalid target"));
        AccessTokenSubjectResolver resolver = resolver(AccessTokenSubjectResolver.Mode.DUAL);

        assertThatThrownBy(() -> resolver.resolve("Bearer " + token))
                .isInstanceOfSatisfying(
                        AuthException.class,
                        exception -> assertThat(exception.code()).isEqualTo("UNAUTHORIZED"));

        verify(targetValidator).validate(token);
        verifyNoInteractions(legacyValidator);
    }

    @Test
    void dualModeSelectsOnlyLegacyForTheExactLegacyProfile() {
        String token = token("""
                {"alg":"HS256","typ":"JWT"}
                """);
        when(legacyValidator.resolveSubject("Bearer " + token))
                .thenReturn("user-0a5b7c1e-5d75-4dc0-a10e-a330d0583930");
        AccessTokenSubjectResolver resolver = resolver(AccessTokenSubjectResolver.Mode.DUAL);

        AccessTokenSubjectResolver.Subject subject = resolver.resolve("Bearer " + token);

        assertThat(subject.resourceUserId())
                .isEqualTo("user-0a5b7c1e-5d75-4dc0-a10e-a330d0583930");
        assertThat(subject.authUserId()).isNull();
        verify(legacyValidator).resolveSubject("Bearer " + token);
        verifyNoInteractions(targetValidator);
    }

    @Test
    void rejectsAmbiguousProfilesBeforeEitherCryptographicValidator() {
        String token = token("""
                {"alg":"HS256","typ":"JWT","kid":"unexpected"}
                """);
        AccessTokenSubjectResolver resolver = resolver(AccessTokenSubjectResolver.Mode.DUAL);

        assertThatThrownBy(() -> resolver.resolve("Bearer " + token))
                .isInstanceOf(AuthException.class);

        verify(legacyValidator, never()).resolveSubject(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(targetValidator);
    }

    @Test
    void explicitModesRejectTheOtherProfileWithoutFallback() {
        String target = token("""
                {"alg":"RS256","typ":"at+jwt","kid":"key-1"}
                """);
        String legacy = token("""
                {"alg":"HS256","typ":"JWT"}
                """);

        assertThatThrownBy(() -> resolver(AccessTokenSubjectResolver.Mode.LEGACY_ONLY)
                        .resolve("Bearer " + target))
                .isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> resolver(AccessTokenSubjectResolver.Mode.TARGET_ONLY)
                        .resolve("Bearer " + legacy))
                .isInstanceOf(AuthException.class);
        verifyNoInteractions(legacyValidator, targetValidator);
    }

    private AccessTokenSubjectResolver resolver(AccessTokenSubjectResolver.Mode mode) {
        return new AccessTokenSubjectResolver(
                mode, legacyValidator, targetValidator, objectMapper);
    }

    private String token(String headerJson) {
        return Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8))
                + ".payload.signature";
    }
}
