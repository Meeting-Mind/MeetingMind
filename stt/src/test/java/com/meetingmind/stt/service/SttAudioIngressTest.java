package com.meetingmind.stt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SttAudioIngressTest {

    @Test
    void dropsOldestQueuedFramesToKeepLiveAudioRecent() throws Exception {
        SttSessionRegistry sessionRegistry = mock(SttSessionRegistry.class);
        CountDownLatch firstSendStarted = new CountDownLatch(1);
        CountDownLatch allowFirstSend = new CountDownLatch(1);
        List<byte[]> sent = new ArrayList<>();
        SttStreamClient client = new SttStreamClient() {
            @Override
            public synchronized void sendAudio(byte[] audio) {
                if (sent.isEmpty()) {
                    firstSendStarted.countDown();
                    try {
                        allowFirstSend.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                }
                sent.add(audio);
            }

            @Override
            public void finishAudio() {
            }

            @Override
            public void close() {
            }
        };
        when(sessionRegistry.getStreamClient("session-1")).thenReturn(client);
        SttAudioIngress ingress = new SttAudioIngress(sessionRegistry, 4);

        ingress.submit(frame("session-1", (byte) 1));
        assertThat(firstSendStarted.await(1, TimeUnit.SECONDS)).isTrue();
        ingress.submit(frame("session-1", (byte) 2));
        ingress.submit(frame("session-1", (byte) 3));

        assertThat(ingress.snapshot("session-1").droppedOldestFrames()).isEqualTo(1);
        allowFirstSend.countDown();
        ingress.finish("session-1");

        assertThat(sent).extracting(audio -> audio[0]).containsExactly((byte) 1, (byte) 3);
        ingress.shutdown();
    }

    @Test
    void doesNotFailTranscriptWhenQueuedAudioCannotSendDuringExplicitStop() throws Exception {
        SttSessionRegistry sessionRegistry = mock(SttSessionRegistry.class);
        SttStreamClient client = new SttStreamClient() {
            @Override
            public void sendAudio(byte[] audio) {
                throw new IllegalStateException("socket closed");
            }

            @Override
            public void finishAudio() {
            }

            @Override
            public void close() {
            }
        };
        when(sessionRegistry.getStreamClient("session-1")).thenReturn(client);
        when(sessionRegistry.isStopping("session-1")).thenReturn(true);
        SttAudioIngress ingress = new SttAudioIngress(sessionRegistry, 4);

        ingress.submit(frame("session-1", (byte) 1));
        ingress.finish("session-1");

        verify(sessionRegistry, never()).failAndClose("session-1");
        ingress.shutdown();
    }

    private static AudioFrame frame(String sessionId, byte marker) {
        return new AudioFrame(sessionId, "meeting-1", null, "track-1", marker, 0,
                new byte[]{marker, 0, 0, 0}, 16_000, 1, 16);
    }
}
