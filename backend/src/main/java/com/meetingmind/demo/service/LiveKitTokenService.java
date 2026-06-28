package com.meetingmind.demo.service;

import com.meetingmind.demo.dto.LiveKitTokenRequest;
import com.meetingmind.demo.dto.LiveKitTokenResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class LiveKitTokenService {

    private static final Path DOTENV_PATH = Path.of(".env");

    public LiveKitTokenResponse issueToken(LiveKitTokenRequest request) {
        String serverUrl = requireConfig("LIVEKIT_WS_URL", "LIVEKIT_URL");
        String apiKey = requireEnv("LIVEKIT_API_KEY");
        String apiSecret = requireEnv("LIVEKIT_API_SECRET");

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

    private String requireEnv(String key) {
        return requireConfig(key);
    }

    private String requireConfig(String... keys) {
        Map<String, String> dotenv = loadDotEnv();

        for (String key : keys) {
            String value = System.getenv(key);

            if (value != null && !value.isBlank()) {
                return value;
            }

            String fileValue = dotenv.get(key);
            if (fileValue != null && !fileValue.isBlank()) {
                return fileValue;
            }
        }

        if (keys.length == 1) {
            throw new IllegalStateException(keys[0] + " 환경변수가 설정되지 않았습니다.");
        }

        throw new IllegalStateException(String.join(" 또는 ", keys) + " 환경변수가 설정되지 않았습니다.");
    }

    private Map<String, String> loadDotEnv() {
        Map<String, String> values = new HashMap<>();

        if (!Files.exists(DOTENV_PATH)) {
            return values;
        }

        try {
            List<String> lines = Files.readAllLines(DOTENV_PATH, StandardCharsets.UTF_8);

            for (String line : lines) {
                String trimmed = line.trim();

                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }

                int separatorIndex = trimmed.indexOf('=');
                String key = trimmed.substring(0, separatorIndex).trim();
                String value = trimmed.substring(separatorIndex + 1).trim();
                values.put(key, stripQuotes(value));
            }
        } catch (IOException exception) {
            throw new IllegalStateException(".env 파일을 읽는 중 오류가 발생했습니다.", exception);
        }

        return values;
    }

    private String stripQuotes(String value) {
        if (value.length() >= 2) {
            boolean doubleQuoted = value.startsWith("\"") && value.endsWith("\"");
            boolean singleQuoted = value.startsWith("'") && value.endsWith("'");

            if (doubleQuoted || singleQuoted) {
                return value.substring(1, value.length() - 1);
            }
        }

        return value;
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
