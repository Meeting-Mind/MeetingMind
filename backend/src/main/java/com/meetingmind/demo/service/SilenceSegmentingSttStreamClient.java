package com.meetingmind.demo.service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

// 실측 결과 Clova는 스트림을 끝까지 닫아야만 확정된 인식 결과를 안정적으로 준다(중간 epFlag만으로는
// 확정이 안 옴). 그래서 화자가 SILENCE_MS 이상 조용하면 지금 열려 있는 Clova gRPC 스트림을 닫아서
// 그 구간을 확정시키고, 다시 말하기 시작하면 새 스트림을 연다. LiveKit egress/웹소켓은 계속 유지되고
// Clova 연결만 침묵 단위로 갈아끼운다.
public class SilenceSegmentingSttStreamClient implements SttStreamClient {

    private static final long SILENCE_MS = 5_000;
    // 16bit PCM 기준 대략적인 무음 임계치. 방 소음 수준에 따라 조정 필요할 수 있음.
    private static final int SILENCE_RMS_THRESHOLD = 400;

    private final SttStreamClientFactory factory;
    private final Consumer<String> onFinalTranscript;
    private final Consumer<String> onPartialTranscript;
    private final Consumer<Throwable> onError;
    private final long silenceMs;
    private final LongSupplier clock;

    private SttStreamClient current;
    private long lastVoiceAtMs;
    private boolean closed;

    public SilenceSegmentingSttStreamClient(
            SttStreamClientFactory factory,
            Consumer<String> onFinalTranscript,
            Consumer<String> onPartialTranscript,
            Consumer<Throwable> onError
    ) {
        this(factory, onFinalTranscript, onPartialTranscript, onError, SILENCE_MS, System::currentTimeMillis);
    }

    // 테스트에서 5초 대기 없이 침묵 타임아웃을 검증할 수 있도록 시간 소스를 주입한다.
    SilenceSegmentingSttStreamClient(
            SttStreamClientFactory factory,
            Consumer<String> onFinalTranscript,
            Consumer<String> onPartialTranscript,
            Consumer<Throwable> onError,
            long silenceMs,
            LongSupplier clock
    ) {
        this.factory = factory;
        this.onFinalTranscript = onFinalTranscript;
        this.onPartialTranscript = onPartialTranscript;
        this.onError = onError;
        this.silenceMs = silenceMs;
        this.clock = clock;
        this.current = factory.create(onFinalTranscript, onPartialTranscript, onError);
        this.lastVoiceAtMs = clock.getAsLong();
    }

    @Override
    public synchronized void sendAudio(byte[] pcm16leMono16k) {
        if (closed || pcm16leMono16k == null || pcm16leMono16k.length == 0) {
            return;
        }

        long now = clock.getAsLong();
        if (!isSilence(pcm16leMono16k)) {
            lastVoiceAtMs = now;
            if (current == null) {
                current = factory.create(onFinalTranscript, onPartialTranscript, onError);
            }
            current.sendAudio(pcm16leMono16k);
            return;
        }

        if (current == null) {
            return;
        }

        if (now - lastVoiceAtMs >= silenceMs) {
            current.close();
            current = null;
        } else {
            current.sendAudio(pcm16leMono16k);
        }
    }

    @Override
    public synchronized void finishAudio() {
        if (current != null) {
            current.finishAudio();
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (current != null) {
            current.close();
            current = null;
        }
    }

    boolean hasOpenStream() {
        return current != null;
    }

    private static boolean isSilence(byte[] pcm16le) {
        ByteBuffer buffer = ByteBuffer.wrap(pcm16le).order(ByteOrder.LITTLE_ENDIAN);
        int sampleCount = pcm16le.length / 2;
        if (sampleCount == 0) {
            return true;
        }
        long sumSquares = 0;
        for (int i = 0; i < sampleCount; i++) {
            short sample = buffer.getShort(i * 2);
            sumSquares += (long) sample * sample;
        }
        double rms = Math.sqrt((double) sumSquares / sampleCount);
        return rms < SILENCE_RMS_THRESHOLD;
    }
}
