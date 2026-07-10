package com.meetingmind.demo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AuthServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-07T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void signupIssuesTokenPairAndCurrentUserCanResolveAccessToken() {
        AuthService service = newAuthService(new FakeGoogleCredentialVerifier());

        AuthTokenResponse response = service.signup(
                new SignupRequest("MIJU@MeetingMind.ai", "password-123", "이미주"),
                "JUnit"
        );

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).startsWith("mmr_");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3_600L);
        assertThat(response.refreshExpiresIn()).isEqualTo(1_209_600L);
        assertThat(response.user().email()).isEqualTo("miju@meetingmind.ai");

        AuthUserResponse currentUser = service.currentUser("Bearer " + response.accessToken());
        assertThat(currentUser.id()).isEqualTo(response.user().id());
        assertThat(currentUser.displayName()).isEqualTo("이미주");
    }

    @Test
    void signupRejectsDuplicateEmail() {
        AuthService service = newAuthService(new FakeGoogleCredentialVerifier());
        service.signup(new SignupRequest("miju@meetingmind.ai", "password-123", "이미주"), "JUnit");

        assertThatThrownBy(() -> service.signup(new SignupRequest("MIJU@meetingmind.ai", "password-456", "미주"), "JUnit"))
                .isInstanceOf(AuthException.class)
                .satisfies(error -> {
                    AuthException exception = (AuthException) error;
                    assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.code()).isEqualTo("EMAIL_ALREADY_REGISTERED");
                });
    }

    @Test
    void signupRejectsPasswordThatDoesNotMeetPolicy() {
        AuthService service = newAuthService(new FakeGoogleCredentialVerifier());

        assertThatThrownBy(() -> service.signup(new SignupRequest("miju@meetingmind.ai", "password", "이미주"), "JUnit"))
                .isInstanceOf(AuthException.class)
                .satisfies(error -> {
                    AuthException exception = (AuthException) error;
                    assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.code()).isEqualTo("INVALID_REQUEST");
                });
    }

    @Test
    void loginRejectsWrongPassword() {
        AuthService service = newAuthService(new FakeGoogleCredentialVerifier());
        service.signup(new SignupRequest("miju@meetingmind.ai", "password-123", "이미주"), "JUnit");

        assertThatThrownBy(() -> service.login(new LoginRequest("miju@meetingmind.ai", "wrong-password"), "JUnit"))
                .isInstanceOf(AuthException.class)
                .satisfies(error -> {
                    AuthException exception = (AuthException) error;
                    assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.code()).isEqualTo("INVALID_CREDENTIALS");
                });
    }

    @Test
    void refreshRotatesRefreshTokenAndRejectsOldToken() {
        AuthService service = newAuthService(new FakeGoogleCredentialVerifier());
        AuthTokenResponse signup = service.signup(
                new SignupRequest("miju@meetingmind.ai", "password-123", "이미주"),
                "JUnit"
        );

        AuthTokenResponse refreshed = service.refresh(new RefreshTokenRequest(signup.refreshToken()), "JUnit");

        assertThat(refreshed.user().id()).isEqualTo(signup.user().id());
        assertThat(refreshed.refreshToken()).isNotEqualTo(signup.refreshToken());
        assertThat(service.currentUser("Bearer " + refreshed.accessToken()).id()).isEqualTo(signup.user().id());
        assertThatThrownBy(() -> service.refresh(new RefreshTokenRequest(signup.refreshToken()), "JUnit"))
                .isInstanceOf(AuthException.class)
                .satisfies(error -> assertThat(((AuthException) error).code()).isEqualTo("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void googleLoginLinksVerifiedGoogleIdentityToExistingEmail() {
        FakeGoogleCredentialVerifier verifier = new FakeGoogleCredentialVerifier();
        verifier.add(
                "google-token",
                new GoogleUserInfo("google-sub-001", "miju@meetingmind.ai", "이미주 Google", "https://image.example/miju.png")
        );
        AuthService service = newAuthService(verifier);
        AuthTokenResponse localSignup = service.signup(
                new SignupRequest("miju@meetingmind.ai", "password-123", "이미주"),
                "JUnit"
        );

        AuthTokenResponse googleLogin = service.googleLogin(new GoogleLoginRequest("google-token"), "JUnit");

        assertThat(googleLogin.user().id()).isEqualTo(localSignup.user().id());
        assertThat(googleLogin.user().email()).isEqualTo("miju@meetingmind.ai");
    }

    private static AuthService newAuthService(GoogleCredentialVerifier googleCredentialVerifier) {
        AuthEnvironment environment = new AuthEnvironment(Map.of("MEETINGMIND_JWT_SECRET", "test-secret")::get);
        AuthTokenService tokenService = new AuthTokenService(environment, FIXED_CLOCK, new SecureRandom());
        return new AuthService(
                new InMemoryAuthStore(),
                new PasswordHasher(new SecureRandom()),
                tokenService,
                googleCredentialVerifier
        );
    }

    private static final class FakeGoogleCredentialVerifier implements GoogleCredentialVerifier {
        private final Map<String, GoogleUserInfo> responses = new java.util.HashMap<>();

        void add(String credential, GoogleUserInfo response) {
            responses.put(credential, response);
        }

        @Override
        public GoogleUserInfo verify(String credential) {
            GoogleUserInfo response = responses.get(credential);
            if (response == null) {
                throw new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Google credential 검증에 실패했습니다.");
            }
            return response;
        }
    }
}
