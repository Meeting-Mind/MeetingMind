package com.meetingmind.stt.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

final class OpenAiTranscriptEventMapper {

    private final SttTranscriptEventMapper eventMapper;
    private final StringBuilder partialText = new StringBuilder();

    OpenAiTranscriptEventMapper(SttSessionContext context) {
        this.eventMapper = new SttTranscriptEventMapper("openai-realtime", context);
    }

    List<TranscriptEvent> map(JsonNode providerEvent) {
        String type = providerEvent.path("type").asText("");
        String eventId = providerEvent.path("event_id").asText("");
        String segmentId = providerEvent.path("item_id").asText(null);
        if ("conversation.item.input_audio_transcription.delta".equals(type)) {
            String delta = providerEvent.path("delta").asText("");
            if (delta.isBlank()) {
                return List.of();
            }
            partialText.append(delta);
            return List.of(eventMapper.event(
                    TranscriptEventType.PARTIAL,
                    eventId,
                    segmentId,
                    partialText.toString(),
                    0,
                    null,
                    false
            ));
        }
        if ("conversation.item.input_audio_transcription.segment".equals(type)) {
            return finalEvent(providerEvent.path("text").asText(""), eventId, segmentId,
                    providerEvent.path("start").asDouble(0), providerEvent.path("end").asDouble(0));
        }
        if ("conversation.item.input_audio_transcription.completed".equals(type)) {
            return finalEvent(providerEvent.path("transcript").asText(""), eventId, segmentId, 0, 0);
        }
        return List.of();
    }

    private List<TranscriptEvent> finalEvent(String text, String eventId, String segmentId, double start, double end) {
        if (text.isBlank()) {
            return List.of();
        }
        partialText.setLength(0);
        long startMs = Math.round(start * 1000);
        long endMs = Math.round(end * 1000);
        return List.of(eventMapper.event(
                TranscriptEventType.FINAL,
                eventId,
                segmentId,
                text,
                startMs,
                endMs > 0 ? endMs : null,
                true
        ));
    }
}
