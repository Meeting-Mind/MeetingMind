package com.meetingmind.demo.service;

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
}
