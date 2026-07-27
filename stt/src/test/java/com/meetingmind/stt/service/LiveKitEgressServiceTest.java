package com.meetingmind.stt.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LiveKitEgressServiceTest {

    @Test
    void convertsWebSocketUrlToHttpApiUrl() {
        assertThat(LiveKitEgressService.egressApiUrl("wss://livekit.example.test"))
                .isEqualTo("https://livekit.example.test");
        assertThat(LiveKitEgressService.egressApiUrl("ws://localhost:7880"))
                .isEqualTo("http://localhost:7880");
    }

    @Test
    void keepsHttpApiUrlUnchanged() {
        assertThat(LiveKitEgressService.egressApiUrl("https://livekit.example.test"))
                .isEqualTo("https://livekit.example.test");
    }

    @Test
    void convertsPublicIngressBaseUrlToWebSocketUrl() {
        assertThat(LiveKitEgressService.egressWebSocketUrl("https://example.ngrok-free.dev", "session-1", "tok"))
                .isEqualTo("wss://example.ngrok-free.dev/ws/egress-audio/session-1?token=tok");
        assertThat(LiveKitEgressService.egressWebSocketUrl("http://127.0.0.1:8080/", "session-2", "tok"))
                .isEqualTo("ws://127.0.0.1:8080/ws/egress-audio/session-2?token=tok");
    }

    @Test
    void treatsAlreadyTerminalEgressStopAsIdempotent() {
        assertThat(LiveKitEgressService.isTerminalStopConflict(
                412,
                "{\"code\":\"failed_precondition\",\"msg\":\"egress with status EGRESS_FAILED cannot be stopped\"}"
        )).isTrue();
        assertThat(LiveKitEgressService.isTerminalStopConflict(
                412,
                "{\"code\":\"failed_precondition\",\"msg\":\"egress with status EGRESS_COMPLETE cannot be stopped\"}"
        )).isTrue();
        assertThat(LiveKitEgressService.isTerminalStopConflict(412, "track is not ready")).isFalse();
        assertThat(LiveKitEgressService.isTerminalStopConflict(500, "EGRESS_FAILED")).isFalse();
    }
}
