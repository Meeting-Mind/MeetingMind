package com.meetingmind.demo.service;

import com.meetingmind.demo.config.DotenvConfig;
import com.meetingmind.demo.dto.LiveKitTokenRequest;
import com.meetingmind.demo.dto.LiveKitTokenResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class LiveKitTokenService {

    public LiveKitTokenResponse issueToken(LiveKitTokenRequest request) {
        String serverUrl = DotenvConfig.require("LIVEKIT_WS_URL", "LIVEKIT_URL");
        String apiKey = DotenvConfig.require("LIVEKIT_API_KEY");
        String apiSecret = DotenvConfig.require("LIVEKIT_API_SECRET");

        long now = Instant.now().getEpochSecond();
        long expiresAt = now + 60L * 60L;

        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url(buildPayload(request, apiKey, now, expiresAt));
        String signature = sign(apiSecret, header + "." + payload);

        return new LiveKitTokenResponse(
                serverUrl,
                header + "." + payload + "." + signature,
                request.roomName(),
                request.identity(),
                request.name()
        );
    }

    private String buildPayload(LiveKitTokenRequest request, String apiKey, long now, long expiresAt) {
        return "{"
                + "\"iss\":\"" + escape(apiKey) + "\","
                + "\"sub\":\"" + escape(request.identity()) + "\","
                + "\"name\":\"" + escape(request.name()) + "\","
                + "\"nbf\":" + now + ","
                + "\"exp\":" + expiresAt + ","
                + "\"video\":{"
                + "\"roomJoin\":true,"
                + "\"room\":\"" + escape(request.roomName()) + "\","
                + "\"canPublish\":true,"
                + "\"canSubscribe\":true,"
                + "\"canPublishData\":true"
                + "}"
                + "}";
    }

    private String sign(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("LiveKit 토큰 서명에 실패했습니다.", exception);
        }
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
