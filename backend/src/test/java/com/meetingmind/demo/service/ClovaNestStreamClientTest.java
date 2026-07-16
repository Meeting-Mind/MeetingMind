package com.meetingmind.demo.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClovaNestStreamClientTest {

    @Test
    void ignoresConfigurationAcknowledgement() {
        String acknowledgement = """
                {"config":{"status":"Success"},"responseType":["config"]}
                """;

        assertThat(ClovaNestStreamClient.extractTranscription(acknowledgement)).isNull();
    }

    @Test
    void extractsOnlyTranscriptionText() {
        String transcription = """
                {"responseType":["transcription"],"transcription":{"text":"회의 전사 결과입니다."}}
                """;

        assertThat(ClovaNestStreamClient.extractTranscription(transcription))
                .isEqualTo("회의 전사 결과입니다.");
    }

    @Test
    void recognizesProviderRecognitionFailure() {
        String failure = """
                {"responseType":["recognize"],"recognize":{"status":"Invalid format"}}
                """;

        assertThat(ClovaNestStreamClient.isRecognitionFailure(failure)).isTrue();
    }
}
