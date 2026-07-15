package com.meetingmind.demo.auth;

import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthStore store;
    private final PasswordHasher passwordHasher;
    private final AuthTokenService tokenService;
    private final GoogleCredentialVerifier googleCredentialVerifier;

    public AuthService(
            AuthStore store,
            PasswordHasher passwordHasher,
            AuthTokenService tokenService,
            GoogleCredentialVerifier googleCredentialVerifier
    ) {
        this.store = store;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
        this.googleCredentialVerifier = googleCredentialVerifier;
    }

    @Transactional
    public AuthTokenResponse signup(SignupRequest request, String userAgent) {
        String email = AuthStore.normalizeEmail(request.email());
        if (store.findIdentity("local", email).isPresent() || store.findUserByEmail(email).isPresent()) {
            throw new AuthException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "이미 가입된 이메일입니다.");
        }
        if (!PasswordPolicy.isValid(request.password())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", PasswordPolicy.MESSAGE);
        }

        Instant now = tokenService.now();
        try {
            AuthUser user = store.createUser(email, request.displayName().trim(), null, now);
            store.saveIdentity(user.id(), "local", email, passwordHasher.hash(request.password()), now);
            return issueTokenPair(user, userAgent);
        } catch (DataIntegrityViolationException exception) {
            throw new AuthException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "이미 가입된 이메일입니다.");
        }
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request, String userAgent) {
        String email = AuthStore.normalizeEmail(request.email());
        AuthIdentity identity = store.findIdentity("local", email)
                .orElseThrow(this::invalidCredentials);
        if (!passwordHasher.matches(request.password(), identity.passwordHash())) {
            throw invalidCredentials();
        }

        Instant now = tokenService.now();
        store.touchIdentity(identity, now);
        AuthUser user = store.findUserById(identity.userId())
                .map(found -> store.touchLogin(found, now))
                .orElseThrow(this::invalidCredentials);
        return issueTokenPair(user, userAgent);
    }

    @Transactional
    public AuthTokenResponse googleLogin(GoogleLoginRequest request, String userAgent) {
        GoogleUserInfo googleUser = googleCredentialVerifier.verify(request.credential());
        Instant now = tokenService.now();

        AuthIdentity identity = store.findIdentity("google", googleUser.providerUserId()).orElse(null);
        if (identity != null) {
            store.touchIdentity(identity, now);
            AuthUser user = store.findUserById(identity.userId())
                    .map(found -> store.touchLogin(found, now))
                    .orElseThrow(this::invalidCredentials);
            return issueTokenPair(user, userAgent);
        }

        AuthUser user = store.findUserByEmail(googleUser.email())
                .map(found -> store.touchLogin(found, now))
                .orElseGet(() -> store.createUser(
                        googleUser.email(),
                        googleUser.displayName(),
                        googleUser.pictureUrl(),
                        now
                ));
        store.saveIdentity(user.id(), "google", googleUser.providerUserId(), null, now);
        return issueTokenPair(user, userAgent);
    }

    @Transactional
    public AuthTokenResponse refresh(RefreshTokenRequest request, String userAgent) {
        String refreshTokenHash = tokenService.hashRefreshToken(request.refreshToken());
        Instant now = tokenService.now();
        RefreshTokenSession session = store.findRefreshSessionForUpdate(refreshTokenHash)
                .filter(found -> found.isActive(now))
                .orElseThrow(this::invalidRefreshToken);
        store.revokeRefreshSession(refreshTokenHash, now);

        AuthUser user = store.findUserById(session.userId())
                .map(found -> store.touchLogin(found, now))
                .orElseThrow(this::invalidRefreshToken);
        return issueTokenPair(user, userAgent);
    }

    @Transactional(readOnly = true)
    public AuthUserResponse currentUser(String authorizationHeader) {
        String userId = tokenService.resolveSubject(authorizationHeader);
        return store.findUserById(userId)
                .map(AuthUserResponse::from)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "사용자를 찾을 수 없습니다."));
    }

    @Transactional
    public LogoutResponse logout(String authorizationHeader, LogoutRequest request) {
        tokenService.resolveSubject(authorizationHeader);
        String refreshTokenHash = tokenService.hashRefreshToken(request.refreshToken());
        Instant now = tokenService.now();
        RefreshTokenSession session = store.findRefreshSessionForUpdate(refreshTokenHash)
                .orElseThrow(this::invalidRefreshToken);
        if (!session.isActive(now)) {
            throw invalidRefreshToken();
        }

        store.revokeRefreshSession(refreshTokenHash, now);
        return new LogoutResponse(true);
    }

    private AuthTokenResponse issueTokenPair(AuthUser user, String userAgent) {
        String accessToken = tokenService.issueAccessToken(user);
        String refreshToken = tokenService.issueRefreshToken();
        Instant now = tokenService.now();
        store.saveRefreshSession(
                user.id(),
                tokenService.hashRefreshToken(refreshToken),
                now,
                now.plusSeconds(tokenService.refreshTokenSeconds()),
                userAgent
        );

        return new AuthTokenResponse(
                accessToken,
                refreshToken,
                "Bearer",
                tokenService.accessTokenSeconds(),
                tokenService.refreshTokenSeconds(),
                AuthUserResponse.from(user)
        );
    }

    private AuthException invalidCredentials() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    private AuthException invalidRefreshToken() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "refresh token이 올바르지 않습니다.");
    }
}
