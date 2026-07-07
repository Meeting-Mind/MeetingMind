package com.meetingmind.demo.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class AuthTokenService {

    static final long ACCESS_TOKEN_SECONDS = 3_600L;
    static final long REFRESH_TOKEN_SECONDS = 1_209_600L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AuthEnvironment environment;
    private final Clock clock;
    private final SecureRandom secureRandom;

    public AuthTokenService(AuthEnvironment environment) {
        this(environment, Clock.systemUTC(), new SecureRandom());
    }

    AuthTokenService(AuthEnvironment environment, Clock clock, SecureRandom secureRandom) {
        this.environment = environment;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    Instant now() {
        return Instant.now(clock);
    }

    long accessTokenSeconds() {
        return ACCESS_TOKEN_SECONDS;
    }

    long refreshTokenSeconds() {
        return REFRESH_TOKEN_SECONDS;
    }

    String issueAccessToken(AuthUser user) {
        long issuedAt = now().getEpochSecond();
        long expiresAt = issuedAt + ACCESS_TOKEN_SECONDS;
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("typ", "access");
        payload.put("sub", user.id());
        payload.put("email", user.email());
        payload.put("name", user.displayName());
        payload.put("iat", issuedAt);
        payload.put("exp", expiresAt);

        String encodedHeader = base64Url(toJson(header));
        String encodedPayload = base64Url(toJson(payload));
        String signature = sign(encodedHeader + "." + encodedPayload);
        return encodedHeader + "." + encodedPayload + "." + signature;
    }

    String issueRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return "mmr_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    String hashRefreshToken(String refreshToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("refresh token hash 생성에 실패했습니다.", exception);
        }
    }

    String resolveSubject(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "access token이 필요합니다.");
        }

        String token = authorizationHeader.substring("Bearer ".length()).trim();
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "access token 형식이 올바르지 않습니다.");
        }

        String expectedSignature = sign(parts[0] + "." + parts[1]);
        if (!constantTimeEquals(expectedSignature, parts[2])) {
            throw new AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "access token 서명이 올바르지 않습니다.");
        }

        Map<String, Object> payload = decodeJson(parts[1]);
        if (!"access".equals(payload.get("typ"))) {
            throw new AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "access token 유형이 올바르지 않습니다.");
        }

        Object expiresAtValue = payload.get("exp");
        if (!(expiresAtValue instanceof Number expiresAtNumber)) {
            throw new AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "access token 만료 시간이 없습니다.");
        }

        long expiresAt = expiresAtNumber.longValue();
        if (expiresAt <= now().getEpochSecond()) {
            throw new AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "access token이 만료되었습니다.");
        }

        Object subject = payload.get("sub");
        if (!(subject instanceof String value) || value.isBlank()) {
            throw new AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "access token subject가 없습니다.");
        }

        return value;
    }

    private String sign(String value) {
        try {
            String secret = environment.requireConfig("MEETINGMIND_JWT_SECRET", "AUTH_JWT_SECRET");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (AuthException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("access token 서명에 실패했습니다.", exception);
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private static String toJson(Map<String, Object> values) {
        try {
            return OBJECT_MAPPER.writeValueAsString(values);
        } catch (Exception exception) {
            throw new IllegalStateException("token JSON 생성에 실패했습니다.", exception);
        }
    }

    private static Map<String, Object> decodeJson(String tokenPart) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(tokenPart);
            return OBJECT_MAPPER.readValue(decoded, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "access token payload를 읽을 수 없습니다.");
        }
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
