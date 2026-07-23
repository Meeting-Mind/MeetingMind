package com.meetingmind.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.demo.dto.stt.MeetingTranscriptGatewayResponse;
import com.meetingmind.demo.dto.stt.TranscriptionStartGatewayRequest;
import com.meetingmind.demo.dto.stt.TranscriptionStartGatewayResponse;
import com.meetingmind.demo.dto.stt.TranscriptionStatusGatewayResponse;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HttpTranscriptionGatewayTest {

    @Test
    void startsATranscriptionAgainstTheInternalSttApi() throws Exception {
        AtomicReference<String> receivedPath = new AtomicReference<>();
        AtomicReference<String> receivedMethod = new AtomicReference<>();
        AtomicReference<String> receivedToken = new AtomicReference<>();
        AtomicReference<String> receivedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/transcriptions", exchange -> {
            receivedPath.set(exchange.getRequestURI().getPath());
            receivedMethod.set(exchange.getRequestMethod());
            receivedToken.set(exchange.getRequestHeaders().getFirst("X-MeetingMind-Service-Token"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"sessionId\":\"session-1\",\"status\":\"PROCESSING\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            HttpTranscriptionGateway gateway = new HttpTranscriptionGateway(
                    HttpClient.newHttpClient(), new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort(), "internal-test-token"
            );

            TranscriptionStartGatewayResponse response = gateway.start(new TranscriptionStartGatewayRequest(
                    "meeting-1", "meeting-1-room", "track-1", "2026-08-21T00:00:00Z", "request-1"
            ));

            assertThat(response.sessionId()).isEqualTo("session-1");
            assertThat(receivedMethod.get()).isEqualTo("POST");
            assertThat(receivedPath.get()).isEqualTo("/internal/v1/transcriptions");
            assertThat(receivedToken.get()).isEqualTo("internal-test-token");
            assertThat(receivedBody.get()).contains("\"meetingId\":\"meeting-1\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void stopsASessionByPostingToTheSessionStopPath() throws Exception {
        AtomicReference<String> receivedPath = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/transcriptions/session-1/stop", exchange -> {
            receivedPath.set(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            HttpTranscriptionGateway gateway = new HttpTranscriptionGateway(
                    HttpClient.newHttpClient(), new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort(), ""
            );

            gateway.stop("session-1");

            assertThat(receivedPath.get()).isEqualTo("/internal/v1/transcriptions/session-1/stop");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void readsMeetingTranscriptAndStatus() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/meetings/meeting-1/transcript", exchange -> {
            byte[] response = ("{\"meetingId\":\"meeting-1\",\"status\":\"COMPLETED\",\"segments\":["
                    + "{\"id\":\"seg-1\",\"speakerId\":\"spk-1\",\"speakerLabel\":\"A\",\"speakerName\":\"Kim\","
                    + "\"startMs\":0,\"endMs\":1200,\"text\":\"hello\"}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/internal/v1/meetings/meeting-1/transcription-status", exchange -> {
            byte[] response = "{\"meetingId\":\"meeting-1\",\"status\":\"COMPLETED\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            HttpTranscriptionGateway gateway = new HttpTranscriptionGateway(
                    HttpClient.newHttpClient(), new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort(), ""
            );

            MeetingTranscriptGatewayResponse transcript = gateway.transcript("meeting-1");
            TranscriptionStatusGatewayResponse status = gateway.status("meeting-1");

            assertThat(transcript.segments()).hasSize(1);
            assertThat(transcript.segments().get(0).text()).isEqualTo("hello");
            assertThat(status.status().name()).isEqualTo("COMPLETED");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void wrapsNonSuccessResponsesInATranscriptionGatewayException() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/meetings/meeting-1/transcription-status", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        try {
            HttpTranscriptionGateway gateway = new HttpTranscriptionGateway(
                    HttpClient.newHttpClient(), new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort(), ""
            );

            assertThatThrownBy(() -> gateway.status("meeting-1"))
                    .isInstanceOf(TranscriptionGatewayException.class);
        } finally {
            server.stop(0);
        }
    }
}
