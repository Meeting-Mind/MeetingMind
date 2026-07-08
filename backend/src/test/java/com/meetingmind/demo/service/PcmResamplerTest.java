package com.meetingmind.demo.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class PcmResamplerTest {

    @Test
    void downsamplesLengthByFactorOfThree() {
        short[] samples = new short[300];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = 1000;
        }
        byte[] input = toBytes(samples);

        byte[] output = PcmResampler.downsample48kTo16kMono(input);

        assertThat(output.length).isEqualTo(input.length / 3);
    }

    @Test
    void preservesConstantAmplitude() {
        short[] samples = new short[9];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = 2000;
        }
        byte[] input = toBytes(samples);

        byte[] output = PcmResampler.downsample48kTo16kMono(input);
        ByteBuffer out = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN);

        assertThat(out.getShort(0)).isEqualTo((short) 2000);
        assertThat(out.getShort(2)).isEqualTo((short) 2000);
        assertThat(out.getShort(4)).isEqualTo((short) 2000);
    }

    private byte[] toBytes(short[] samples) {
        ByteBuffer buffer = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : samples) {
            buffer.putShort(sample);
        }
        return buffer.array();
    }
}
