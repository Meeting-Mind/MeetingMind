package com.meetingmind.stt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.stt.config.DotenvConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class OpenAiRealtimeSttProvider implements SttProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiRealtimeSttProvider.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String providerId() {
        return "openai-realtime";
    }

    @Override
    public SttStreamClient createClient(
            SttSessionContext context,
            Consumer<TranscriptEvent> onTranscriptEvent,
            Consumer<Throwable> onError
    ) {
        return new OpenAiRealtimeSttStreamClient(context, onTranscriptEvent, onError);
    }

    static final class OpenAiRealtimeSttStreamClient implements SttStreamClient, WebSocket.Listener {

        private final Consumer<TranscriptEvent> onTranscriptEvent;
        private final Consumer<Throwable> onError;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final OpenAiTranscriptEventMapper eventMapper;
        private final CountDownLatch connected = new CountDownLatch(1);
        private volatile WebSocket webSocket;

        OpenAiRealtimeSttStreamClient(
                SttSessionContext context,
                Consumer<TranscriptEvent> onTranscriptEvent,
                Consumer<Throwable> onError
        ) {
            this.onTranscriptEvent = onTranscriptEvent;
            this.onError = onError;
            this.eventMapper = new OpenAiTranscriptEventMapper(context);
            connect();
        }

        @Override
        public synchronized void sendAudio(byte[] pcm16leMono16k) {
            if (closed.get() || pcm16leMono16k == null || pcm16leMono16k.length == 0) {
                return;
            }
            awaitConnected();
            byte[] pcm24k = PcmResampler.resample16kMonoTo24kMono(pcm16leMono16k);
            sendEvent("""
                    {"event_id":"%s","type":"input_audio_buffer.append","audio":"%s"}
                    """.formatted(
                    eventId(),
                    Base64.getEncoder().encodeToString(pcm24k)
            ));
        }

        @Override
        public synchronized void finishAudio() {
            if (closed.get()) {
                return;
            }
            awaitConnected();
            sendEvent("""
                    {"event_id":"%s","type":"input_audio_buffer.commit"}
                    """.formatted(eventId()));
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
            sendEvent("""
                    {
                      "event_id":"%s",
                      "type":"session.update",
                      "session":{
                        "type":"transcription",
                        "audio":{
                          "input":{
                            "format":{"type":"audio/pcm","rate":24000},
                            "noise_reduction":{"type":"near_field"},
                            "transcription":{"model":"gpt-4o-transcribe","language":"ko"}
                          }
                        },
                        "turn_detection":{"type":"server_vad","prefix_padding_ms":300,"silence_duration_ms":500}
                      }
                    }
                    """.formatted(eventId()));
            connected.countDown();
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            try {
                JsonNode event = OBJECT_MAPPER.readTree(data.toString());
                handleEvent(event);
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
            log.warn("OpenAI realtime STT socket error", error);
            onError.accept(error);
        }

        private void connect() {
            try {
                String apiKey = DotenvConfig.require("OPENAI_API_KEY", "OPEN_AI_KEY");
                String model = DotenvConfig.optional("OPENAI_REALTIME_TRANSCRIBE_MODEL").orElse("gpt-4o-transcribe");
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();
                client.newWebSocketBuilder()
                        .header("Authorization", "Bearer " + apiKey)
                        .header("OpenAI-Beta", "realtime=v1")
                        .buildAsync(URI.create("wss://api.openai.com/v1/realtime?model=" + model), this)
                        .join();
            } catch (Exception exception) {
                connected.countDown();
                throw new IllegalStateException("OpenAI realtime transcription 연결에 실패했습니다.", exception);
            }
        }

        private void handleEvent(JsonNode event) {
            String type = event.path("type").asText("");
            if ("error".equals(type)) {
                String message = event.path("error").path("message").asText("OpenAI realtime transcription error");
                onError.accept(new IllegalStateException(message));
                return;
            }
            eventMapper.map(event).forEach(onTranscriptEvent);
        }

        private void sendEvent(String payload) {
            if (webSocket == null) {
                throw new IllegalStateException("OpenAI realtime transcription websocket is not connected.");
            }
            webSocket.sendText(payload, true).join();
        }

        private void awaitConnected() {
            try {
                if (!connected.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("OpenAI realtime transcription websocket 연결 대기 시간이 초과되었습니다.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("OpenAI realtime transcription websocket 연결 대기 중 인터럽트되었습니다.", exception);
            }
        }

        private String eventId() {
            return "event-" + UUID.randomUUID();
        }
    }
}
