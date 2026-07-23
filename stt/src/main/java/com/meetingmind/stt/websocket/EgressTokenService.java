package com.meetingmind.stt.websocket;

import com.meetingmind.stt.config.DotenvConfig;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Short-lived, one-time signed token for the LiveKit egress WebSocket URL.
 * Replaces the previous {@code setAllowedOrigins("*")}-only guard: the egress
 * worker is not a browser, so Origin checks don't authenticate it — this does.
 */
@Component
public class EgressTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long TOKEN_TTL_SECONDS = 300L;

    private final Clock clock;

    public EgressTokenService() {
        this(Clock.systemUTC());
    }

    EgressTokenService(Clock clock) {
        this.clock = clock;
    }

    public String issue(String sessionId) {
        long expiresAt = Instant.now(clock).getEpochSecond() + TOKEN_TTL_SECONDS;
        String payload = sessionId + ":" + expiresAt;
        String signature = sign(payload);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload + ":" + signature).getBytes(StandardCharsets.UTF_8));
    }

    public boolean verify(String sessionId, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        String[] parts = decoded.split(":", 3);
        if (parts.length != 3) {
            return false;
        }
        String tokenSessionId = parts[0];
        String expiresAtRaw = parts[1];
        String signature = parts[2];
        long expiresAt;
        try {
            expiresAt = Long.parseLong(expiresAtRaw);
        } catch (NumberFormatException exception) {
            return false;
        }
        if (!MessageDigest.isEqual(
                tokenSessionId.getBytes(StandardCharsets.UTF_8), sessionId.getBytes(StandardCharsets.UTF_8))) {
            return false;
        }
        if (Instant.now(clock).getEpochSecond() > expiresAt) {
            return false;
        }
        String expectedSignature = sign(tokenSessionId + ":" + expiresAtRaw);
        return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    DotenvConfig.require("STT_EGRESS_WS_SECRET").getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception exception) {
            throw new IllegalStateException("egress websocket 토큰 서명에 실패했습니다.", exception);
        }
    }
}
