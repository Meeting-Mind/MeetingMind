package com.meetingmind.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Soniox finalizes tokens individually, while {@code <end>} marks the end of an utterance.
 * Keep finalized tokens in the live buffer until that endpoint so one spoken sentence becomes
 * one persisted TranscriptSegment.
 */
final class SonioxTranscriptEventMapper {

    private final SttTranscriptEventMapper eventMapper;
    private final StringBuilder finalizedText = new StringBuilder();
    private long utteranceSequence = 1;
    private long utteranceStartMs = -1;
    private long utteranceEndMs;

    SonioxTranscriptEventMapper(SttSessionContext context) {
        this.eventMapper = new SttTranscriptEventMapper("soniox-realtime", context);
    }

    List<TranscriptEvent> map(JsonNode providerEvent) {
        JsonNode tokensNode = providerEvent.path("tokens");
        if (!tokensNode.isArray() || tokensNode.isEmpty()) {
            return List.of();
        }

        StringBuilder partialText = new StringBuilder();
        boolean endpointDetected = false;
        for (JsonNode token : tokensNode) {
            String text = token.path("text").asText("");
            if (isEndpoint(token, text)) {
                endpointDetected = true;
                continue;
            }
            if (text.isBlank()) {
                continue;
            }

            recordTimestamp(token);
            if (token.path("is_final").asBoolean(false)) {
                finalizedText.append(text);
            } else {
                partialText.append(text);
            }
        }

        if (endpointDetected) {
            return finalizeUtterance();
        }

        String visibleText = finalizedText + partialText.toString();
        if (TranscriptTextSanitizer.sanitize(visibleText).isBlank()) {
            return List.of();
        }
        String liveId = "soniox-live-" + utteranceSequence;
        return List.of(eventMapper.event(
                TranscriptEventType.PARTIAL,
                liveId,
                liveId,
                visibleText,
                utteranceStartMs < 0 ? 0 : utteranceStartMs,
                utteranceEndMs,
                false
        ));
    }

    private List<TranscriptEvent> finalizeUtterance() {
        String text = finalizedText.toString();
        if (TranscriptTextSanitizer.sanitize(text).isBlank()) {
            resetUtterance();
            return List.of();
        }

        String finalId = "soniox-final-" + utteranceSequence;
        TranscriptEvent event = eventMapper.event(
                TranscriptEventType.FINAL,
                finalId,
                finalId,
                text,
                utteranceStartMs < 0 ? 0 : utteranceStartMs,
                utteranceEndMs,
                true
        );
        resetUtterance();
        return List.of(event);
    }

    private void recordTimestamp(JsonNode token) {
        long startMs = token.path("start_ms").asLong(0);
        long endMs = token.path("end_ms").asLong(startMs);
        if (utteranceStartMs < 0) {
            utteranceStartMs = startMs;
        }
        utteranceEndMs = Math.max(utteranceEndMs, endMs);
    }

    private void resetUtterance() {
        finalizedText.setLength(0);
        utteranceSequence++;
        utteranceStartMs = -1;
        utteranceEndMs = 0;
    }

    private static boolean isEndpoint(JsonNode token, String text) {
        return token.path("is_final").asBoolean(false) && "<end>".equals(text.strip());
    }
}
