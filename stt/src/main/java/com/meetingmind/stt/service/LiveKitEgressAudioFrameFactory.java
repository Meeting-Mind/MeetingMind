package com.meetingmind.stt.service;

import org.springframework.stereotype.Component;

/** Creates source frames using the PCM format emitted by LiveKit Track Egress. */
@Component
public class LiveKitEgressAudioFrameFactory {

    public AudioFrame create(SttSessionContext context, long sequence, long capturedAtMs, byte[] pcm16le) {
        return new AudioFrame(
                context.sessionId(),
                context.meetingId(),
                context.participantId(),
                context.trackId(),
                sequence,
                capturedAtMs,
                pcm16le,
                LiveKitEgressAudioFrameNormalizer.LIVEKIT_SAMPLE_RATE_HZ,
                LiveKitEgressAudioFrameNormalizer.LIVEKIT_CHANNEL_COUNT,
                LiveKitEgressAudioFrameNormalizer.PCM_BITS_PER_SAMPLE
        );
    }
}
