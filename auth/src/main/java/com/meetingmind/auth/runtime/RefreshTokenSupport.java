package com.meetingmind.auth.runtime;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
class RefreshTokenSupport {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec hashKey;

    RefreshTokenSupport(AuthRuntimeProperties properties) {
        this.hashKey = new SecretKeySpec(
                properties.refreshHashSecret().getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
        );
    }

    String issue() {
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        return "mmr_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    String hash(String token) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(hashKey);
            return "hmac_sha256$" + Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("refresh token hash 생성에 실패했습니다.", exception);
        }
    }
}
