package com.meetingmind.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.demo.dto.LiveKitTokenRequest;
import com.meetingmind.demo.dto.LiveKitTokenResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class LiveKitTokenServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Instant FIXED_NOW = Instant.parse("2026-07-07T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Test
    void issueTokenBuildsLiveKitJwtWithExpectedClaims() throws Exception {
        Map<String, String> config = Map.of(
                "LIVEKIT_URL", "wss://livekit.example.test",
                "LIVEKIT_API_KEY", "test-api-key",
                "LIVEKIT_API_SECRET", "test-api-secret"
        );
        LiveKitTokenService service = new LiveKitTokenService(FIXED_CLOCK, config::get);

        LiveKitTokenResponse response = service.issueToken(
                new LiveKitTokenRequest("MM-03A", "user-123", "이미주")
        );

        assertThat(response.serverUrl()).isEqualTo("wss://livekit.example.test");
        assertThat(response.roomName()).isEqualTo("MM-03A");
        assertThat(response.identity()).isEqualTo("user-123");
        assertThat(response.name()).isEqualTo("이미주");

        String[] tokenParts = response.participantToken().split("\\.");
        assertThat(tokenParts).hasSize(3);

        Map<String, Object> header = decodeJson(tokenParts[0]);
        assertThat(header)
                .containsEntry("alg", "HS256")
                .containsEntry("typ", "JWT");

        Map<String, Object> payload = decodeJson(tokenParts[1]);
        assertThat(payload)
                .containsEntry("iss", "test-api-key")
                .containsEntry("sub", "user-123")
                .containsEntry("name", "이미주");
        assertThat(((Number) payload.get("nbf")).longValue()).isEqualTo(FIXED_NOW.getEpochSecond());
        assertThat(((Number) payload.get("exp")).longValue()).isEqualTo(FIXED_NOW.getEpochSecond() + 3600L);

        @SuppressWarnings("unchecked")
        Map<String, Object> video = (Map<String, Object>) payload.get("video");
        assertThat(video)
                .containsEntry("roomJoin", true)
                .containsEntry("room", "MM-03A")
                .containsEntry("canPublish", true)
                .containsEntry("canSubscribe", true)
                .containsEntry("canPublishData", true);

        assertThat(tokenParts[2]).isEqualTo(sign("test-api-secret", tokenParts[0] + "." + tokenParts[1]));
    }

    @Test
    void issueTokenFailsWhenRequiredConfigIsMissing() {
        Map<String, String> config = Map.of(
                "LIVEKIT_WS_URL", "wss://livekit.example.test",
                "LIVEKIT_API_KEY", "test-api-key"
        );
        LiveKitTokenService service = new LiveKitTokenService(FIXED_CLOCK, config::get);

        assertThatThrownBy(() -> service.issueToken(new LiveKitTokenRequest("MM-03A", "user-123", "이미주")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LIVEKIT_API_SECRET");
    }

    private static Map<String, Object> decodeJson(String tokenPart) throws Exception {
        byte[] decoded = Base64.getUrlDecoder().decode(tokenPart);
        return OBJECT_MAPPER.readValue(decoded, new TypeReference<>() {
        });
    }

    private static String sign(String secret, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal(value.getBytes(StandardCharsets.UTF_8))
        );
    }
}
