package com.meetingmind.auth.runtime;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Issues opaque, single-use password reset tokens. The raw value is never persisted. */
@Component
class PasswordResetTokenSupport {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final byte[] DOMAIN = "meetingmind:password-reset:v1\u0000".getBytes(StandardCharsets.UTF_8);

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec hashKey;

    PasswordResetTokenSupport(AuthRuntimeProperties properties) {
        this.hashKey = new SecretKeySpec(
                properties.refreshHashSecret().getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
        );
    }

    String issue() {
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        return "mmpr_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    String hash(String token) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(hashKey);
            mac.update(DOMAIN);
            return "hmac_sha256$" + Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("password reset token hash 생성에 실패했습니다.", exception);
        }
    }
}
