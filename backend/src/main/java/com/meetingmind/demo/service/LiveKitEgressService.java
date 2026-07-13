package com.meetingmind.demo.service;

import com.meetingmind.demo.config.DotenvConfig;
import io.livekit.server.EgressServiceClient;
import java.io.IOException;
import livekit.LivekitEgress;
import org.springframework.stereotype.Service;
import retrofit2.Call;
import retrofit2.Response;

@Service
public class LiveKitEgressService {

    public String startTrackEgress(String roomName, String trackId, String websocketUrl) {
        Call<LivekitEgress.EgressInfo> call = client().startTrackEgress(roomName, websocketUrl, trackId);
        LivekitEgress.EgressInfo info = execute(call, "LiveKit Egress 시작에 실패했습니다.");
        return info.getEgressId();
    }

    public void stopEgress(String egressId) {
        execute(client().stopEgress(egressId), "LiveKit Egress 중지에 실패했습니다.");
    }

    private EgressServiceClient client() {
        String host = DotenvConfig.require("LIVEKIT_WS_URL", "LIVEKIT_URL");
        String apiKey = DotenvConfig.require("LIVEKIT_API_KEY");
        String apiSecret = DotenvConfig.require("LIVEKIT_API_SECRET");
        return EgressServiceClient.Companion.create(host, apiKey, apiSecret);
    }

    private LivekitEgress.EgressInfo execute(Call<LivekitEgress.EgressInfo> call, String errorMessage) {
        try {
            Response<LivekitEgress.EgressInfo> response = call.execute();
            if (!response.isSuccessful() || response.body() == null) {
                throw new IllegalStateException(errorMessage + " (HTTP " + response.code() + ")");
            }
            return response.body();
        } catch (IOException exception) {
            throw new IllegalStateException(errorMessage, exception);
        }
    }
}
