package com.meetingmind.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class LiveKitEgressAudioFrameNormalizerTest {

    private final LiveKitEgressAudioFrameNormalizer normalizer = new LiveKitEgressAudioFrameNormalizer();

    @Test
    void normalizes48kStereoPcmAndPreservesFrameIdentity() {
        byte[] sourcePcm = stereoPcm(9, (short) 1_200, (short) 2_400);
        AudioFrame normalized = normalizer.normalize(new AudioFrame(
                "session-1", "meeting-1", "participant-1", "track-1", 7, 1234,
                sourcePcm, 48_000, 2, 16
        ));

        assertThat(normalized.sessionId()).isEqualTo("session-1");
        assertThat(normalized.meetingId()).isEqualTo("meeting-1");
        assertThat(normalized.participantId()).isEqualTo("participant-1");
        assertThat(normalized.trackId()).isEqualTo("track-1");
        assertThat(normalized.sequence()).isEqualTo(7);
        assertThat(normalized.capturedAtMs()).isEqualTo(1234);
        assertThat(normalized.sampleRateHz()).isEqualTo(16_000);
        assertThat(normalized.channelCount()).isEqualTo(1);
        assertThat(normalized.bitsPerSample()).isEqualTo(16);
        assertThat(samples(normalized.pcm16le())).containsExactly((short) 1_800, (short) 1_800, (short) 1_800);
    }

    @Test
    void rejectsUnsupportedOrIncompleteLiveKitFrames() {
        assertThatThrownBy(() -> normalizer.normalize(new AudioFrame(
                "session-1", "meeting-1", null, "track-1", 0, 0,
                new byte[4], 16_000, 1, 16
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported");

        assertThatThrownBy(() -> normalizer.normalize(new AudioFrame(
                "session-1", "meeting-1", null, "track-1", 0, 0,
                new byte[5], 48_000, 2, 16
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid");
    }

    private static byte[] stereoPcm(int frameCount, short left, short right) {
        ByteBuffer buffer = ByteBuffer.allocate(frameCount * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < frameCount; i++) {
            buffer.putShort(left);
            buffer.putShort(right);
        }
        return buffer.array();
    }

    private static short[] samples(byte[] pcm) {
        ByteBuffer buffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        short[] samples = new short[pcm.length / 2];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = buffer.getShort();
        }
        return samples;
    }
}
