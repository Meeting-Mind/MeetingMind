package com.meetingmind.demo.service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class PcmResampler {

    private PcmResampler() {
    }

    // ponytail: 48kHz -> 16kHz는 정수비(3:1)라 3샘플 평균으로 다운샘플. 정식 저역통과 필터 아님,
    // STT 용도로는 충분한 근사치. 음질 문제 생기면 정식 리샘플러로 교체.
    public static byte[] downsample48kTo16kMono(byte[] pcm16leMono48k) {
        int sampleCount = pcm16leMono48k.length / 2;
        int outSampleCount = sampleCount / 3;

        ByteBuffer in = ByteBuffer.wrap(pcm16leMono48k).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer out = ByteBuffer.allocate(outSampleCount * 2).order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < outSampleCount; i++) {
            int a = in.getShort(i * 6);
            int b = in.getShort(i * 6 + 2);
            int c = in.getShort(i * 6 + 4);
            short avg = (short) ((a + b + c) / 3);
            out.putShort(avg);
        }

        return out.array();
    }
}
