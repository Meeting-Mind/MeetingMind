package com.meetingmind.stt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class SilenceSegmentingSttStreamClientTest {

    private static final long SILENCE_MS = 5_000;

    @Test
    void keepsSameStreamWhileSilenceIsUnderThreshold() {
        AtomicLong clock = new AtomicLong(0);
        Deque<SttStreamClient> created = new ArrayDeque<>();
        SttStreamClient first = mock(SttStreamClient.class);
        created.add(first);

        SilenceSegmentingSttStreamClient client = new SilenceSegmentingSttStreamClient(
                factoryReturning(created), chunkNoop(), chunkNoop(), errorNoop(), SILENCE_MS, clock::get
        );

        client.sendAudio(voiced());
        clock.addAndGet(SILENCE_MS - 1);
        client.sendAudio(silence());

        verify(first, never()).close();
        assertThat(client.hasOpenStream()).isTrue();
    }

    @Test
    void closesStreamAfterSilenceThresholdAndReopensOnNextVoice() {
        AtomicLong clock = new AtomicLong(0);
        SttStreamClient first = mock(SttStreamClient.class);
        SttStreamClient second = mock(SttStreamClient.class);
        Deque<SttStreamClient> created = new ArrayDeque<>();
        created.add(first);
        created.add(second);
        AtomicInteger finalFlushCount = new AtomicInteger(0);

        SilenceSegmentingSttStreamClient client = new SilenceSegmentingSttStreamClient(
                factoryReturning(created), ignored -> finalFlushCount.incrementAndGet(), chunkNoop(), errorNoop(), SILENCE_MS, clock::get
        );

        client.sendAudio(voiced());
        clock.addAndGet(SILENCE_MS);
        client.sendAudio(silence());

        verify(first).close();
        assertThat(client.hasOpenStream()).isFalse();
        assertThat(finalFlushCount).hasValue(1);

        client.sendAudio(voiced());
        assertThat(client.hasOpenStream()).isTrue();
        verify(second).sendAudio(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void flushesFinalBoundaryWhenClientCloses() {
        AtomicLong clock = new AtomicLong(0);
        Deque<SttStreamClient> created = new ArrayDeque<>();
        SttStreamClient first = mock(SttStreamClient.class);
        created.add(first);
        AtomicInteger finalFlushCount = new AtomicInteger(0);

        SilenceSegmentingSttStreamClient client = new SilenceSegmentingSttStreamClient(
                factoryReturning(created), ignored -> finalFlushCount.incrementAndGet(), chunkNoop(), errorNoop(), SILENCE_MS, clock::get
        );

        client.close();

        verify(first, times(1)).close();
        assertThat(finalFlushCount).hasValue(1);
    }

    private static RawSttStreamClientFactory factoryReturning(Deque<SttStreamClient> queue) {
        return (onFinal, onPartial, onError) -> queue.poll();
    }

    private static Consumer<SttTranscriptChunk> chunkNoop() {
        return ignored -> {
        };
    }

    private static Consumer<Throwable> errorNoop() {
        return ignored -> {
        };
    }

    private static byte[] voiced() {
        return pcm((short) 20_000, 160);
    }

    private static byte[] silence() {
        return pcm((short) 0, 160);
    }

    private static byte[] pcm(short amplitude, int sampleCount) {
        ByteBuffer buffer = ByteBuffer.allocate(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < sampleCount; i++) {
            buffer.putShort(amplitude);
        }
        return buffer.array();
    }
}
