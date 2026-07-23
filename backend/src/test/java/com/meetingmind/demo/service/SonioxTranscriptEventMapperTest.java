package com.meetingmind.demo.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class SonioxTranscriptEventMapperTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void keepsFinalAndUnfinalizedTokensInOneLiveUtteranceUntilEndpoint() throws Exception {
        SonioxTranscriptEventMapper mapper = new SonioxTranscriptEventMapper(
                new SttSessionContext("session-1", "meeting-1", "participant-1", "track-1")
        );

        List<TranscriptEvent> first = mapper.map(OBJECT_MAPPER.readTree("""
                {"tokens":[{"text":"우리","is_final":false,"start_ms":0,"end_ms":100}]}
                """));
        List<TranscriptEvent> second = mapper.map(OBJECT_MAPPER.readTree("""
                {"tokens":[
                  {"text":"우리","is_final":true,"start_ms":0,"end_ms":100},
                  {"text":"는","is_final":false,"start_ms":100,"end_ms":200}
                ]}
                """));

        assertThat(first).extracting(TranscriptEvent::text).containsExactly("우리");
        assertThat(second)
                .extracting(TranscriptEvent::text)
                .containsExactly("우리는");
        assertThat(second)
                .extracting(TranscriptEvent::type)
                .containsExactly(TranscriptEventType.PARTIAL);
    }

    @Test
    void persistsOneFinalUtteranceOnlyWhenSonioxSendsEndpoint() throws Exception {
        SonioxTranscriptEventMapper mapper = new SonioxTranscriptEventMapper(
                new SttSessionContext("session-1", "meeting-1", "participant-1", "track-1")
        );

        List<TranscriptEvent> first = mapper.map(OBJECT_MAPPER.readTree("""
                {"tokens":[{"text":"첫 문장","is_final":true,"start_ms":0,"end_ms":300}]}
                """));
        List<TranscriptEvent> second = mapper.map(OBJECT_MAPPER.readTree("""
                {"tokens":[{"text":"입니다.","is_final":true,"start_ms":400,"end_ms":800}]}
                """));
        List<TranscriptEvent> endpoint = mapper.map(OBJECT_MAPPER.readTree("""
                {"tokens":[{"text":"<end>","is_final":true,"start_ms":800,"end_ms":800}]}
                """));

        assertThat(first).extracting(TranscriptEvent::type).containsExactly(TranscriptEventType.PARTIAL);
        assertThat(second).extracting(TranscriptEvent::text).containsExactly("첫 문장입니다.");
        assertThat(endpoint).extracting(TranscriptEvent::text).containsExactly("첫 문장입니다.");
        assertThat(endpoint).extracting(TranscriptEvent::type).containsExactly(TranscriptEventType.FINAL);
    }
}
