package com.meetingmind.stt.websocket;

import com.meetingmind.stt.service.AudioFrame;
import com.meetingmind.stt.service.AudioFrameNormalizer;
import com.meetingmind.stt.service.LiveKitEgressAudioFrameFactory;
import com.meetingmind.stt.service.SttAudioIngress;
import com.meetingmind.stt.service.SttSessionRegistry;
import com.meetingmind.stt.service.SttSessionContext;
import com.meetingmind.stt.service.SttStreamClient;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

@Component
public class EgressAudioWebSocketHandler extends BinaryWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(EgressAudioWebSocketHandler.class);
    // ponytail: stereo->mono 믹스다운 수정이 실제로 먹혔는지 귀로 확인하려는 임시 덤프.
    // 정상 음성으로 들리는 거 확인되면 이 필드/메서드 통째로 제거한다.
    private static final Path DEBUG_DIR = Path.of("output", "debug-audio");
    private final Map<String, ByteArrayOutputStream> debug16k = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> frameSequences = new ConcurrentHashMap<>();
    private final boolean debugAudioDumpEnabled;

    private final SttSessionRegistry sessionRegistry;
    private final SttAudioIngress audioIngress;
    private final LiveKitEgressAudioFrameFactory audioFrameFactory;
    private final AudioFrameNormalizer audioFrameNormalizer;

    public EgressAudioWebSocketHandler(
            SttSessionRegistry sessionRegistry,
            SttAudioIngress audioIngress,
            LiveKitEgressAudioFrameFactory audioFrameFactory,
            AudioFrameNormalizer audioFrameNormalizer
    ) {
        this.sessionRegistry = sessionRegistry;
        this.audioIngress = audioIngress;
        this.audioFrameFactory = audioFrameFactory;
        this.audioFrameNormalizer = audioFrameNormalizer;
        this.debugAudioDumpEnabled = com.meetingmind.stt.config.DotenvConfig
                .optional("STT_DEBUG_AUDIO_DUMP")
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        String sessionId = extractSessionId(session);
        SttStreamClient client = sessionRegistry.getStreamClient(sessionId);
        if (client == null) {
            return;
        }

        SttSessionContext context = sessionRegistry.getSessionContext(sessionId);
        if (context == null) {
            return;
        }

        byte[] pcm48k = new byte[message.getPayloadLength()];
        message.getPayload().get(pcm48k);
        long sequence = frameSequences
                .computeIfAbsent(sessionId, ignored -> new AtomicLong())
                .getAndIncrement();

        AudioFrame normalized;
        try {
            normalized = audioFrameNormalizer.normalize(
                    audioFrameFactory.create(context, sequence, System.currentTimeMillis(), pcm48k)
            );
        } catch (IllegalArgumentException exception) {
            log.warn("Dropped invalid egress audio frame for session {}: {}", sessionId, exception.getMessage());
            return;
        }

        if (debugAudioDumpEnabled) {
            debug16k.computeIfAbsent(sessionId, ignored -> new ByteArrayOutputStream()).writeBytes(normalized.pcm16le());
        }
        audioIngress.submit(normalized);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        String sessionId = extractSessionId(session);
        audioIngress.finish(sessionId);
        if (debugAudioDumpEnabled) {
            dumpWav(sessionId, debug16k.remove(sessionId));
        }
        sessionRegistry.onEgressClosed(sessionId);
        if (sessionRegistry.getStreamClient(sessionId) == null) {
            frameSequences.remove(sessionId);
        }
    }

    private void dumpWav(String sessionId, ByteArrayOutputStream buffer) {
        if (buffer == null || buffer.size() == 0) {
            return;
        }
        try {
            Files.createDirectories(DEBUG_DIR);
            byte[] pcm = buffer.toByteArray();
            AudioFormat format = new AudioFormat(16_000, 16, 1, true, false);
            AudioInputStream audioStream = new AudioInputStream(
                    new ByteArrayInputStream(pcm), format, pcm.length / format.getFrameSize());
            Path target = DEBUG_DIR.resolve(sessionId + "-16k-1ch.wav");
            AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, target.toFile());
            log.info("Dumped debug audio: {}", target.toAbsolutePath());
        } catch (Exception exception) {
            log.warn("Failed to dump debug audio for {}", sessionId, exception);
        }
    }

    private String extractSessionId(WebSocketSession session) {
        String path = session.getUri().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
