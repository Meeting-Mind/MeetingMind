package com.meetingmind.demo.service;

import org.springframework.stereotype.Component;

/** Converts the fixed LiveKit Track Egress PCM format into the STT input format. */
@Component
public class LiveKitEgressAudioFrameNormalizer implements AudioFrameNormalizer {

    static final int LIVEKIT_SAMPLE_RATE_HZ = 48_000;
    static final int LIVEKIT_CHANNEL_COUNT = 2;
    static final int PCM_BITS_PER_SAMPLE = 16;
    static final int STT_SAMPLE_RATE_HZ = 16_000;
    static final int STT_CHANNEL_COUNT = 1;

    @Override
    public AudioFrame normalize(AudioFrame source) {
        if (source.sampleRateHz() != LIVEKIT_SAMPLE_RATE_HZ
                || source.channelCount() != LIVEKIT_CHANNEL_COUNT
                || source.bitsPerSample() != PCM_BITS_PER_SAMPLE) {
            throw new IllegalArgumentException("unsupported LiveKit egress audio format");
        }

        byte[] pcm48kStereo = source.pcm16le();
        int bytesPerFrame = LIVEKIT_CHANNEL_COUNT * (PCM_BITS_PER_SAMPLE / Byte.SIZE);
        if (pcm48kStereo.length == 0 || pcm48kStereo.length % bytesPerFrame != 0) {
            throw new IllegalArgumentException("invalid LiveKit egress PCM frame");
        }

        return new AudioFrame(
                source.sessionId(),
                source.meetingId(),
                source.participantId(),
                source.trackId(),
                source.sequence(),
                source.capturedAtMs(),
                PcmResampler.downsample48kTo16kMono(pcm48kStereo),
                STT_SAMPLE_RATE_HZ,
                STT_CHANNEL_COUNT,
                PCM_BITS_PER_SAMPLE
        );
    }
}
