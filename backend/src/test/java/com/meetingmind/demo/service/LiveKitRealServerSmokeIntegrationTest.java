package com.meetingmind.demo.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.demo.config.DotenvConfig;
import com.meetingmind.demo.dto.LiveKitTokenResponse;
import io.livekit.server.RoomServiceClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import livekit.LivekitModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import retrofit2.Call;
import retrofit2.Response;

/**
 * SMK-002 provider tier: LiveKit 실서버 도달성 검증.
 *
 * <p>기존 `MeetingLiveKitTokenServiceTest`는 `LiveKitTokenService`를 mock으로 대체하므로
 * 권한 분기와 응답 매핑만 검증하고, 자격증명이 실제로 유효한지나 서버에 닿는지는 확인하지
 * 않는다. 이 테스트는 실제 LiveKit 서버에 room을 만들고 조회한 뒤 삭제해 그 공백을 메운다.
 *
 * <p>매체(media) join까지는 다루지 않는다. 실제 오디오/비디오 publish는 브라우저 client가
 * 필요하므로 product E2E 수동 절차로 남는다.
 *
 * <p>기본 비활성이며 `RUN_LIVEKIT_SMOKE=true`일 때만 실행된다.
 */
@EnabledIfEnvironmentVariable(named = "RUN_LIVEKIT_SMOKE", matches = "true")
class LiveKitRealServerSmokeIntegrationTest {

    @Test
    void createsAndDeletesRoomOnRealLiveKitServer() throws Exception {
        String apiUrl = LiveKitEgressService.egressApiUrl(
                DotenvConfig.require("LIVEKIT_WS_URL", "LIVEKIT_URL")
        );
        String apiKey = DotenvConfig.require("LIVEKIT_API_KEY");
        String apiSecret = DotenvConfig.require("LIVEKIT_API_SECRET");

        RoomServiceClient client = RoomServiceClient.Companion.create(apiUrl, apiKey, apiSecret, true);
        String roomName = "smk002-livekit-smoke-" + UUID.randomUUID();

        LivekitModels.Room created = execute(
                client.createRoom(roomName, 60, 4),
                "LiveKit room 생성에 실패했다."
        );
        assertThat(created.getName()).isEqualTo(roomName);

        try {
            List<LivekitModels.Room> rooms = execute(
                    client.listRooms(List.of(roomName)),
                    "LiveKit room 조회에 실패했다."
            );
            assertThat(rooms)
                    .as("생성한 room이 실제 서버에서 조회돼야 한다")
                    .extracting(LivekitModels.Room::getName)
                    .contains(roomName);

            // 같은 room에 대해 발급한 참가자 token이 실제 자격증명으로 서명되는지 확인한다.
            LiveKitTokenResponse token = new LiveKitTokenService()
                    .issueToken(roomName, "smk002-participant", "SMK-002 Participant");
            assertThat(token.participantToken()).isNotBlank();

            JsonNode claims = decodeJwtPayload(token.participantToken());
            assertThat(claims.path("iss").asText())
                    .as("token issuer는 LiveKit API key여야 한다")
                    .isEqualTo(apiKey);
            assertThat(claims.hasNonNull("exp")).as("token에 만료가 있어야 한다").isTrue();
            assertThat(claims.path("video").path("room").asText())
                    .as("token이 대상 room으로 스코프돼야 한다")
                    .isEqualTo(roomName);
            assertThat(token.serverUrl()).isNotBlank();
        } finally {
            execute(client.deleteRoom(roomName), "LiveKit room 삭제에 실패했다.");
        }

        List<LivekitModels.Room> afterDelete = execute(
                client.listRooms(List.of(roomName)),
                "삭제 후 LiveKit room 조회에 실패했다."
        );
        assertThat(afterDelete)
                .as("smoke가 서버에 room을 남기지 않아야 한다")
                .extracting(LivekitModels.Room::getName)
                .doesNotContain(roomName);
    }

    /**
     * java-jwt는 livekit-server의 전이 의존이라 test compile classpath에 없다.
     * 서명 검증이 목적이 아니라 claim 스코프 확인이므로 payload만 직접 디코드한다.
     */
    private static JsonNode decodeJwtPayload(String jwt) throws IOException {
        String[] parts = jwt.split("\\.");
        assertThat(parts).as("JWT는 3개 segment여야 한다").hasSize(3);
        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        return new ObjectMapper().readTree(new String(payload, StandardCharsets.UTF_8));
    }

    /**
     * 실패 시 provider 원문 body를 그대로 노출하지 않는다. 자격증명이 섞여 나갈 수 있다.
     */
    private static <T> T execute(Call<T> call, String errorMessage) throws IOException {
        Response<T> response = call.execute();
        if (!response.isSuccessful()) {
            throw new AssertionError(errorMessage + " status=" + response.code());
        }
        return response.body();
    }
}
