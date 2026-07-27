package com.meetingmind.demo.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HttpTranscriptionGatewayTest {

    @Test
    void startsATranscriptionAgainstTheInternalSttApi() throws Exception {
        AtomicReference<String> receivedMethod = new AtomicReference<>();
        AtomicReference<String> receivedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/transcriptions", exchange -> {
            receivedMethod.set(exchange.getRequestMethod());
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"sessionId\":\"session-1\",\"status\":\"PROCESSING\",\"egressId\":\"egress-1\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            HttpTranscriptionGateway gateway = new HttpTranscriptionGateway(
                    HttpClient.newHttpClient(), new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort()
            );

            TranscriptionHandle handle = gateway.start(new TranscriptionStartCommand(
                    "meeting-1", "meeting-1-room", "track-1", "Kim", Instant.parse("2026-08-21T00:00:00Z"), "request-1"
            ));

            assertThat(handle.sessionId()).isEqualTo("session-1");
            assertThat(handle.egressId()).isEqualTo("egress-1");
            assertThat(receivedMethod.get()).isEqualTo("POST");
            assertThat(receivedBody.get()).contains("\"meetingId\":\"meeting-1\"");
            assertThat(receivedBody.get()).contains("\"participantDisplayName\":\"Kim\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void returnsEmptyWhenAnAuthoritativeTranscriptDoesNotExistYet() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/meetings/meeting-1/transcription-status", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        try {
            HttpTranscriptionGateway gateway = new HttpTranscriptionGateway(
                    HttpClient.newHttpClient(), new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort()
            );

            assertThat(gateway.status("meeting-1")).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void looksUpTheActiveSessionForAMeeting() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/meetings/meeting-1/active-session", exchange -> {
            byte[] response = "{\"sessionId\":\"session-1\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            HttpTranscriptionGateway gateway = new HttpTranscriptionGateway(
                    HttpClient.newHttpClient(), new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort()
            );

            assertThat(gateway.activeSessionId("meeting-1")).isEqualTo("session-1");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void stopsASessionByPostingToTheSessionStopPathWithMeetingId() throws Exception {
        AtomicReference<String> receivedQuery = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/transcriptions/session-1/stop", exchange -> {
            receivedQuery.set(exchange.getRequestURI().getQuery());
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            HttpTranscriptionGateway gateway = new HttpTranscriptionGateway(
                    HttpClient.newHttpClient(), new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort()
            );

            gateway.stop("meeting-1", "session-1");

            assertThat(receivedQuery.get()).isEqualTo("meetingId=meeting-1");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void stopThrowsSessionNotFoundOn404() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/transcriptions/session-1/stop", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        try {
            HttpTranscriptionGateway gateway = new HttpTranscriptionGateway(
                    HttpClient.newHttpClient(), new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort()
            );

            assertThatThrownBy(() -> gateway.stop("meeting-1", "session-1"))
                    .isInstanceOf(TranscriptionSessionNotFoundException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void readsPartialsForAMeeting() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/meetings/meeting-1/partials", exchange -> {
            byte[] response = "[{\"speakerLabel\":\"A\",\"text\":\"hel\"}]".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            HttpTranscriptionGateway gateway = new HttpTranscriptionGateway(
                    HttpClient.newHttpClient(), new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort()
            );

            var partials = gateway.partials("meeting-1");

            assertThat(partials).hasSize(1);
            assertThat(partials.get(0).speakerLabel()).isEqualTo("A");
            assertThat(partials.get(0).text()).isEqualTo("hel");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void readsTheAuthoritativeTranscriptForAMeeting() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/meetings/meeting-1/transcript", exchange -> {
            byte[] response = ("{\"meetingId\":\"meeting-1\",\"status\":\"PROCESSING\","
                    + "\"segments\":[{\"id\":\"segment-1\",\"speakerId\":\"speaker-1\","
                    + "\"speakerLabel\":\"A\",\"speakerName\":\"Alice\",\"startMs\":10,"
                    + "\"endMs\":500,\"text\":\"hello\"}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            HttpTranscriptionGateway gateway = new HttpTranscriptionGateway(
                    HttpClient.newHttpClient(), new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort()
            );

            var transcript = gateway.transcript("meeting-1").orElseThrow();

            assertThat(transcript.status()).isEqualTo(com.meetingmind.demo.domain.TranscriptStatus.PROCESSING);
            assertThat(transcript.segments()).extracting(segment -> segment.text()).containsExactly("hello");
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
                    "http://127.0.0.1:" + server.getAddress().getPort()
            );

            assertThatThrownBy(() -> gateway.status("meeting-1"))
                    .isInstanceOf(TranscriptionGatewayException.class);
        } finally {
            server.stop(0);
        }
    }
}
