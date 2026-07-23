package com.meetingmind.demo.service;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Delivers normalized audio to one STT stream in order without blocking the LiveKit egress socket.
 *
 * <p>The bounded queue favors recent audio. Preserving an ever-growing backlog would make live
 * captions stale and violate the low-latency requirement more severely than dropping old frames.</p>
 */
@Component
public class SttAudioIngress {

    private static final Logger log = LoggerFactory.getLogger(SttAudioIngress.class);
    static final int DEFAULT_MAX_QUEUED_BYTES = 16_000 * 2 * 5;
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(10);

    private final SttSessionRegistry sessionRegistry;
    private final Map<String, SessionQueue> queues = new ConcurrentHashMap<>();
    private final ExecutorService dispatcher = Executors.newVirtualThreadPerTaskExecutor();
    private final int maxQueuedBytes;

    @Autowired
    public SttAudioIngress(SttSessionRegistry sessionRegistry) {
        this(sessionRegistry, DEFAULT_MAX_QUEUED_BYTES);
    }

    SttAudioIngress(SttSessionRegistry sessionRegistry, int maxQueuedBytes) {
        if (maxQueuedBytes <= 0) {
            throw new IllegalArgumentException("maxQueuedBytes must be positive");
        }
        this.sessionRegistry = sessionRegistry;
        this.maxQueuedBytes = maxQueuedBytes;
    }

    public void submit(AudioFrame frame) {
        SessionQueue queue = queues.computeIfAbsent(frame.sessionId(), ignored -> new SessionQueue());
        boolean scheduleDrain;
        synchronized (queue) {
            queue.receivedFrames++;
            queue.receivedBytes += frame.pcm16le().length;
            if (!queue.accepting) {
                queue.droppedAfterFinish++;
                return;
            }

            int bytes = frame.pcm16le().length;
            if (bytes > maxQueuedBytes) {
                queue.droppedOversized++;
                return;
            }
            while (queue.queuedBytes + bytes > maxQueuedBytes && !queue.frames.isEmpty()) {
                AudioFrame discarded = queue.frames.removeFirst();
                queue.queuedBytes -= discarded.pcm16le().length;
                queue.droppedOldest++;
            }
            queue.frames.addLast(frame);
            queue.queuedBytes += bytes;
            scheduleDrain = !queue.draining;
            if (scheduleDrain) {
                queue.draining = true;
                queue.drained = new CompletableFuture<>();
            }
        }
        if (scheduleDrain) {
            dispatcher.execute(() -> drain(frame.sessionId(), queue));
        }
    }

    /** Stops accepting frames and waits briefly for queued audio before provider finalization. */
    public void finish(String sessionId) {
        SessionQueue queue = queues.get(sessionId);
        if (queue == null) {
            return;
        }

        CompletableFuture<Void> drained;
        synchronized (queue) {
            queue.accepting = false;
            drained = queue.drained;
        }
        try {
            drained.get(DRAIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            log.warn("Timed out draining STT audio for session {}", sessionId);
        } finally {
            queues.remove(sessionId, queue);
        }
    }

    public AudioIngressSnapshot snapshot(String sessionId) {
        SessionQueue queue = queues.get(sessionId);
        if (queue == null) {
            return AudioIngressSnapshot.empty(sessionId);
        }
        synchronized (queue) {
            return new AudioIngressSnapshot(
                    sessionId,
                    queue.receivedFrames,
                    queue.receivedBytes,
                    queue.sentFrames,
                    queue.sentBytes,
                    queue.droppedOldest,
                    queue.droppedOversized,
                    queue.droppedAfterFinish,
                    queue.sendFailures,
                    queue.frames.size(),
                    queue.queuedBytes
            );
        }
    }

    @PreDestroy
    void shutdown() {
        dispatcher.shutdownNow();
    }

    private void drain(String sessionId, SessionQueue queue) {
        while (true) {
            AudioFrame frame;
            synchronized (queue) {
                frame = queue.frames.pollFirst();
                if (frame == null) {
                    queue.draining = false;
                    queue.drained.complete(null);
                    return;
                }
                queue.queuedBytes -= frame.pcm16le().length;
            }

            try {
                SttStreamClient client = sessionRegistry.getStreamClient(sessionId);
                if (client == null) {
                    synchronized (queue) {
                        queue.draining = false;
                        queue.drained.complete(null);
                    }
                    return;
                }
                byte[] audio = frame.pcm16le();
                client.sendAudio(audio);
                synchronized (queue) {
                    queue.sentFrames++;
                    queue.sentBytes += audio.length;
                }
            } catch (RuntimeException exception) {
                synchronized (queue) {
                    queue.sendFailures++;
                    queue.frames.clear();
                    queue.queuedBytes = 0;
                }
                if (sessionRegistry.isStopping(sessionId)) {
                    log.info("Ignored STT audio send failure while stopping session {}", sessionId);
                } else {
                    log.warn("Failed to send STT audio for session {}", sessionId, exception);
                    sessionRegistry.failAndClose(sessionId);
                }
                synchronized (queue) {
                    queue.draining = false;
                    queue.drained.complete(null);
                }
                return;
            }
        }
    }

    public record AudioIngressSnapshot(
            String sessionId,
            long receivedFrames,
            long receivedBytes,
            long sentFrames,
            long sentBytes,
            long droppedOldestFrames,
            long droppedOversizedFrames,
            long droppedAfterFinishFrames,
            long sendFailures,
            int queuedFrames,
            int queuedBytes
    ) {
        private static AudioIngressSnapshot empty(String sessionId) {
            return new AudioIngressSnapshot(sessionId, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    private static final class SessionQueue {
        private final ArrayDeque<AudioFrame> frames = new ArrayDeque<>();
        private CompletableFuture<Void> drained = CompletableFuture.completedFuture(null);
        private boolean accepting = true;
        private boolean draining;
        private int queuedBytes;
        private long receivedFrames;
        private long receivedBytes;
        private long sentFrames;
        private long sentBytes;
        private long droppedOldest;
        private long droppedOversized;
        private long droppedAfterFinish;
        private long sendFailures;
    }
}
