package com.meetingmind.stt.service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class PcmResampler {

    private PcmResampler() {
    }

    // ponytail: LiveKit Track Egress websocket 출력은 실측 결과 stereo interleaved 48kHz.
    // (L+R)/2로 모노 믹스다운한 뒤, 48kHz -> 16kHz는 정수비(3:1)라 3샘플 평균으로 다운샘플.
    // 정식 저역통과 필터 아님, STT 용도로는 충분한 근사치. 음질 문제 생기면 정식 리샘플러로 교체.
    public static byte[] downsample48kTo16kMono(byte[] pcm16leStereo48k) {
        int frameCount = pcm16leStereo48k.length / 4;
        int outSampleCount = frameCount / 3;

        ByteBuffer in = ByteBuffer.wrap(pcm16leStereo48k).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer out = ByteBuffer.allocate(outSampleCount * 2).order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < outSampleCount; i++) {
            int sum = 0;
            for (int frame = i * 3; frame < i * 3 + 3; frame++) {
                short left = in.getShort(frame * 4);
                short right = in.getShort(frame * 4 + 2);
                sum += (left + right) / 2;
            }
            out.putShort((short) (sum / 3));
        }

        return out.array();
    }

    public static byte[] resample16kMonoTo24kMono(byte[] pcm16leMono16k) {
        int inputSamples = pcm16leMono16k.length / 2;
        if (inputSamples == 0) {
            return new byte[0];
        }

        int outputSamples = inputSamples * 3 / 2;
        ByteBuffer in = ByteBuffer.wrap(pcm16leMono16k).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer out = ByteBuffer.allocate(outputSamples * 2).order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < outputSamples; i++) {
            double sourceIndex = i * (16_000.0 / 24_000.0);
            int leftIndex = (int) Math.floor(sourceIndex);
            int rightIndex = Math.min(leftIndex + 1, inputSamples - 1);
            double ratio = sourceIndex - leftIndex;

            short left = in.getShort(leftIndex * 2);
            short right = in.getShort(rightIndex * 2);
            int interpolated = (int) Math.round(left + (right - left) * ratio);
            out.putShort((short) interpolated);
        }

        return out.array();
    }
}
