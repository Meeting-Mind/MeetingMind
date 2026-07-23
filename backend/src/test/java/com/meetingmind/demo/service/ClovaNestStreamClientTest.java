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
                {"responseType":["transcription"],"transcription":{"text":"회의 전사 결과입니다.","epFlag":true}}
                """;

        assertThat(ClovaNestStreamClient.extractTranscription(transcription))
                .isEqualTo(new SttTranscriptChunk("회의 전사 결과입니다.", true, -1, 0, 0));
    }

    @Test
    void treatsMissingEpFlagAsFinal() {
        String transcription = """
                {"responseType":["transcription"],"transcription":{"text":"화자 정보 없이 확정된 문장입니다."}}
                """;

        assertThat(ClovaNestStreamClient.extractTranscription(transcription).finalChunk()).isTrue();
    }

    @Test
    void extractsPartialTranscriptionWhenEpFlagFalse() {
        String transcription = """
                {"responseType":["transcription"],"transcription":{"text":"듣는 중","epFlag":false}}
                """;

        assertThat(ClovaNestStreamClient.extractTranscription(transcription))
                .isEqualTo(new SttTranscriptChunk("듣는 중", false, -1, 0, 0));
    }

    @Test
    void ignoresEmptyInterimPing() {
        String transcription = """
                {"responseType":["transcription"],"transcription":{"text":"","epFlag":false}}
                """;

        assertThat(ClovaNestStreamClient.extractTranscription(transcription)).isNull();
    }

    @Test
    void keepsEmptyFinalBoundarySignal() {
        String transcription = """
                {"responseType":["transcription"],"transcription":{"text":"","epFlag":true}}
                """;

        assertThat(ClovaNestStreamClient.extractTranscription(transcription))
                .isEqualTo(new SttTranscriptChunk("", true, -1, 0, 0));
    }

    @Test
    void extractsTimingMetadata() {
        String transcription = """
                {"responseType":["transcription"],"transcription":{"text":"회의", "epFlag":false, "position":12, "startTimestamp":401, "endTimestamp":800}}
                """;

        assertThat(ClovaNestStreamClient.extractTranscription(transcription))
                .isEqualTo(new SttTranscriptChunk("회의", false, 12, 401, 800));
    }

    @Test
    void recognizesProviderRecognitionFailure() {
        String failure = """
                {"responseType":["recognize"],"recognize":{"status":"Invalid format"}}
                """;

        assertThat(ClovaNestStreamClient.isRecognitionFailure(failure)).isTrue();
    }
}
