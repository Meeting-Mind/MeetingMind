package com.meetingmind.stt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.stt.config.DotenvConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class SonioxRealtimeSttProvider implements SttProvider {

    private static final Logger log = LoggerFactory.getLogger(SonioxRealtimeSttProvider.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String providerId() {
        return "soniox-realtime";
    }

    @Override
    public SttStreamClient createClient(
            SttSessionContext context,
            Consumer<TranscriptEvent> onTranscriptEvent,
            Consumer<Throwable> onError
    ) {
        return new SonioxRealtimeSttStreamClient(context, onTranscriptEvent, onError);
    }

    static final class SonioxRealtimeSttStreamClient implements SttStreamClient, WebSocket.Listener {

        private final Consumer<TranscriptEvent> onTranscriptEvent;
        private final Consumer<Throwable> onError;
        private final SonioxTranscriptEventMapper eventMapper;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final CountDownLatch connected = new CountDownLatch(1);
        private volatile WebSocket webSocket;

        SonioxRealtimeSttStreamClient(
                SttSessionContext context,
                Consumer<TranscriptEvent> onTranscriptEvent,
                Consumer<Throwable> onError
        ) {
            this.onTranscriptEvent = onTranscriptEvent;
            this.onError = onError;
            this.eventMapper = new SonioxTranscriptEventMapper(context);
            connect();
        }

        @Override
        public synchronized void sendAudio(byte[] pcm16leMono16k) {
            if (closed.get() || pcm16leMono16k == null || pcm16leMono16k.length == 0) {
                return;
            }
            awaitConnected();
            webSocket.sendBinary(ByteBuffer.wrap(pcm16leMono16k), true).join();
        }

        @Override
        public synchronized void finishAudio() {
            if (closed.get()) {
                return;
            }
            awaitConnected();
            webSocket.sendBinary(ByteBuffer.allocate(0), true).join();
        }

        @Override
        public synchronized void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            if (webSocket != null) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
            }
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            this.webSocket = webSocket;
            webSocket.request(1);
            sendConfig();
            connected.countDown();
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            try {
                handleEvent(OBJECT_MAPPER.readTree(data.toString()));
            } catch (Exception exception) {
                onError.accept(exception);
            } finally {
                webSocket.request(1);
            }
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("Soniox realtime STT socket error", error);
            onError.accept(error);
        }

        private void connect() {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();
                client.newWebSocketBuilder()
                        .buildAsync(URI.create("wss://stt-rt.soniox.com/transcribe-websocket"), this)
                        .join();
            } catch (Exception exception) {
                connected.countDown();
                throw new IllegalStateException("Soniox realtime transcription 연결에 실패했습니다.", exception);
            }
        }

        private void sendConfig() {
            String apiKey = DotenvConfig.require("SONIOX_API_KEY");
            String model = DotenvConfig.optional("SONIOX_REALTIME_MODEL").orElse("stt-rt-v5");
            String language = DotenvConfig.optional("SONIOX_LANGUAGE").orElse("ko");
            boolean enableEndpointDetection = DotenvConfig.optional("SONIOX_ENABLE_ENDPOINT_DETECTION")
                    .map(Boolean::parseBoolean)
                    .orElse(true);
            int maxEndpointDelayMs = DotenvConfig.optional("SONIOX_MAX_ENDPOINT_DELAY_MS")
                    .map(Integer::parseInt)
                    .orElse(1200);
            double endpointSensitivity = DotenvConfig.optional("SONIOX_ENDPOINT_SENSITIVITY")
                    .map(Double::parseDouble)
                    .orElse(0.35d);
            int endpointLatencyAdjustmentLevel = DotenvConfig.optional("SONIOX_ENDPOINT_LATENCY_ADJUSTMENT_LEVEL")
                    .map(Integer::parseInt)
                    .orElse(1);
            String payload = """
                    {
                      "api_key": "%s",
                      "model": "%s",
                      "audio_format": "pcm_s16le",
                      "num_channels": 1,
                      "sample_rate": 16000,
                      "language_hints": ["%s"],
                      "enable_endpoint_detection": %s,
                      "max_endpoint_delay_ms": %d,
                      "endpoint_sensitivity": %s,
                      "endpoint_latency_adjustment_level": %d
                    }
                    """.formatted(
                    apiKey,
                    model,
                    language,
                    enableEndpointDetection,
                    maxEndpointDelayMs,
                    Double.toString(endpointSensitivity),
                    endpointLatencyAdjustmentLevel
            );
            webSocket.sendText(payload, true).join();
        }

        private void handleEvent(JsonNode event) {
            if (event.hasNonNull("error_code")) {
                String message = event.path("error_message").asText("Soniox realtime transcription error");
                onError.accept(new IllegalStateException(message));
                return;
            }
            if (event.path("finished").asBoolean(false)) {
                return;
            }

            eventMapper.map(event).forEach(onTranscriptEvent);
        }

        private void awaitConnected() {
            try {
                if (!connected.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Soniox realtime transcription websocket 연결 대기 시간이 초과되었습니다.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Soniox realtime transcription websocket 연결 대기 중 인터럽트되었습니다.", exception);
            }
        }
    }
}
