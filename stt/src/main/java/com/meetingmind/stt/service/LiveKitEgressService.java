package com.meetingmind.stt.service;

import com.meetingmind.stt.config.DotenvConfig;
import io.livekit.server.EgressServiceClient;
import java.io.IOException;
import java.util.Locale;
import livekit.LivekitEgress;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import retrofit2.Call;
import retrofit2.Response;

@Service
public class LiveKitEgressService {

    private static final Logger log = LoggerFactory.getLogger(LiveKitEgressService.class);
    private static final String EGRESS_AUDIO_PATH = "/ws/egress-audio/";

    public String startTrackEgress(String roomName, String trackId, String websocketUrl) {
        Call<LivekitEgress.EgressInfo> call = client().startTrackEgress(roomName, websocketUrl, trackId);
        LivekitEgress.EgressInfo info = execute(call, "LiveKit Egress 시작에 실패했습니다.");
        return info.getEgressId();
    }

    public void stopEgress(String egressId) {
        String errorMessage = "LiveKit Egress 중지에 실패했습니다.";
        try {
            Response<LivekitEgress.EgressInfo> response = client().stopEgress(egressId).execute();
            if (response.isSuccessful() && response.body() != null) {
                return;
            }
            String detail = responseErrorDetail(response.errorBody());
            if (isTerminalStopConflict(response.code(), detail)) {
                log.info("LiveKit Egress가 이미 terminal 상태라 stop을 멱등 완료로 처리합니다. egressId={}", egressId);
                return;
            }
            log.warn("{} responseCode={} responseBody={}", errorMessage, response.code(), detail);
            throw new IllegalStateException(errorMessage + " (HTTP " + response.code() + ", " + detail + ")");
        } catch (IOException exception) {
            log.warn("{} ioError={}", errorMessage, exception.getMessage(), exception);
            throw new IllegalStateException(errorMessage, exception);
        }
    }

    static boolean isTerminalStopConflict(int responseCode, String responseBody) {
        if (responseCode != 412 || responseBody == null) {
            return false;
        }
        String normalized = responseBody.toUpperCase(Locale.ROOT);
        if (!normalized.contains("FAILED_PRECONDITION")) {
            return false;
        }
        return normalized.contains("EGRESS_FAILED")
                || normalized.contains("EGRESS_COMPLETE")
                || normalized.contains("EGRESS_ABORTED");
    }

    private EgressServiceClient client() {
        String host = egressApiUrl(DotenvConfig.require("LIVEKIT_WS_URL", "LIVEKIT_URL"));
        String apiKey = DotenvConfig.require("LIVEKIT_API_KEY");
        String apiSecret = DotenvConfig.require("LIVEKIT_API_SECRET");
        return EgressServiceClient.Companion.create(host, apiKey, apiSecret);
    }

    static String egressApiUrl(String host) {
        if (host.startsWith("wss://")) {
            return "https://" + host.substring("wss://".length());
        }
        if (host.startsWith("ws://")) {
            return "http://" + host.substring("ws://".length());
        }
        return host;
    }

    public static String egressWebSocketUrl(String publicBaseUrl, String sessionId, String egressToken) {
        String baseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        if (baseUrl.startsWith("https://")) {
            baseUrl = "wss://" + baseUrl.substring("https://".length());
        } else if (baseUrl.startsWith("http://")) {
            baseUrl = "ws://" + baseUrl.substring("http://".length());
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String encodedToken = java.net.URLEncoder.encode(egressToken, java.nio.charset.StandardCharsets.UTF_8);
        return baseUrl + EGRESS_AUDIO_PATH + sessionId + "?token=" + encodedToken;
    }

    private LivekitEgress.EgressInfo execute(Call<LivekitEgress.EgressInfo> call, String errorMessage) {
        try {
            Response<LivekitEgress.EgressInfo> response = call.execute();
            if (!response.isSuccessful() || response.body() == null) {
                String detail = responseErrorDetail(response.errorBody());
                log.warn("{} responseCode={} responseBody={}", errorMessage, response.code(), detail);
                throw new IllegalStateException(errorMessage + " (HTTP " + response.code() + ", " + detail + ")");
            }
            return response.body();
        } catch (IOException exception) {
            log.warn("{} ioError={}", errorMessage, exception.getMessage(), exception);
            throw new IllegalStateException(errorMessage, exception);
        }
    }

    private String responseErrorDetail(ResponseBody errorBody) {
        if (errorBody == null) {
            return "empty error body";
        }
        try (errorBody) {
            String text = errorBody.string();
            if (text == null || text.isBlank()) {
                return "empty error body";
            }
            return text.length() > 500 ? text.substring(0, 500) : text;
        } catch (IOException exception) {
            return "unreadable error body: " + exception.getMessage();
        }
    }
}
