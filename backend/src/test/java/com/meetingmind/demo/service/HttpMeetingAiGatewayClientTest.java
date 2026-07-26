package com.meetingmind.demo.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.demo.dto.ai.AiChatResponse;
import com.meetingmind.demo.dto.ai.MeetingAiGatewayChatRequest;
import com.meetingmind.demo.dto.ai.MeetingAiGatewayTermRequest;
import com.meetingmind.demo.dto.ai.TermExplanationResponse;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HttpMeetingAiGatewayClientTest {

    @Test
    void callsTheInternalAiEndpointWithServiceCredentialAndTraceHeader() throws Exception {
        AtomicReference<String> receivedPath = new AtomicReference<>();
        AtomicReference<String> receivedToken = new AtomicReference<>();
        AtomicReference<String> receivedTraceId = new AtomicReference<>();
        AtomicReference<String> receivedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/internal/meeting-ai/chat", exchange -> {
            receivedPath.set(exchange.getRequestURI().getPath());
            receivedToken.set(exchange.getRequestHeaders().getFirst("X-MeetingMind-Service-Token"));
            receivedTraceId.set(exchange.getRequestHeaders().getFirst("X-Request-ID"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"answer\":\"회의 근거입니다.\",\"sources\":[],\"unsupported\":false,\"unsupportedReason\":null,\"model\":\"test\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            HttpMeetingAiGatewayClient client = new HttpMeetingAiGatewayClient(
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "internal-test-token"
            );

            AiChatResponse response = client.chat(new MeetingAiGatewayChatRequest(
                    "space-1", "meeting-1", "무엇을 결정했나요?"
            ));

            assertThat(response.answer()).isEqualTo("회의 근거입니다.");
            assertThat(receivedPath.get()).isEqualTo("/api/internal/meeting-ai/chat");
            assertThat(receivedToken.get()).isEqualTo("internal-test-token");
            assertThat(receivedTraceId.get()).isNotBlank();
            assertThat(receivedBody.get()).contains("\"meetingId\":\"meeting-1\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void callsTheInternalTermEndpointWithAuthorizedMeetingScope() throws Exception {
        AtomicReference<String> receivedPath = new AtomicReference<>();
        AtomicReference<String> receivedToken = new AtomicReference<>();
        AtomicReference<String> receivedTraceId = new AtomicReference<>();
        AtomicReference<String> receivedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/internal/meeting-ai/explain-term", exchange -> {
            receivedPath.set(exchange.getRequestURI().getPath());
            receivedToken.set(exchange.getRequestHeaders().getFirst("X-MeetingMind-Service-Token"));
            receivedTraceId.set(exchange.getRequestHeaders().getFirst("X-Request-ID"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = ("{\"term\":\"RAG\",\"explanation\":\"회의 검색 방식입니다.\","
                    + "\"sourceType\":\"transcript\",\"sources\":[],\"unsupported\":false,"
                    + "\"unsupportedReason\":null,\"model\":\"test\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            HttpMeetingAiGatewayClient client = new HttpMeetingAiGatewayClient(
                    HttpClient.newHttpClient(), new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort(), "internal-test-token"
            );

            TermExplanationResponse response = client.explainTerm(new MeetingAiGatewayTermRequest(
                    "space-1", "meeting-1", "RAG"
            ));

            assertThat(response.explanation()).isEqualTo("회의 검색 방식입니다.");
            assertThat(receivedPath.get()).isEqualTo("/api/internal/meeting-ai/explain-term");
            assertThat(receivedToken.get()).isEqualTo("internal-test-token");
            assertThat(receivedTraceId.get()).isNotBlank();
            assertThat(receivedBody.get()).contains("\"projectId\":\"space-1\"");
            assertThat(receivedBody.get()).contains("\"meetingId\":\"meeting-1\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsCallWhileCircuitIsOpen() {
        HttpMeetingAiGatewayClient client = new HttpMeetingAiGatewayClient(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                "http://127.0.0.1:65535",
                "internal-test-token",
                new AiGatewayGuardPolicy(4, 1, Duration.ofMinutes(1)),
                FixedClock.at("2026-07-25T00:00:00Z")
        );

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                client.chat(new MeetingAiGatewayChatRequest("space-1", "meeting-1", "질문"))
        )).isInstanceOf(AiGatewayException.class);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                client.chat(new MeetingAiGatewayChatRequest("space-1", "meeting-1", "질문"))
        )).isInstanceOf(AiGatewayException.class)
                .hasMessageContaining("temporarily unavailable");
    }

    private static final class FixedClock extends Clock {
        private final Instant instant;

        private FixedClock(Instant instant) {
            this.instant = instant;
        }

        private static FixedClock at(String instant) {
            return new FixedClock(Instant.parse(instant));
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
