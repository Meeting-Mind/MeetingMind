package com.meetingmind.demo.service;

import java.util.Arrays;

/** Internal audio contract between media ingress and an STT provider. */
public record AudioFrame(
        String sessionId,
        String meetingId,
        String participantId,
        String trackId,
        long sequence,
        long capturedAtMs,
        byte[] pcm16le,
        int sampleRateHz,
        int channelCount,
        int bitsPerSample
) {

    public AudioFrame {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        if (sequence < 0 || capturedAtMs < 0) {
            throw new IllegalArgumentException("audio frame sequence and timestamp must be non-negative");
        }
        if (pcm16le == null) {
            throw new IllegalArgumentException("audio frame payload is required");
        }
        if (sampleRateHz <= 0 || channelCount <= 0 || bitsPerSample <= 0) {
            throw new IllegalArgumentException("audio frame format must be positive");
        }
        pcm16le = Arrays.copyOf(pcm16le, pcm16le.length);
    }

    @Override
    public byte[] pcm16le() {
        return Arrays.copyOf(pcm16le, pcm16le.length);
    }
}
