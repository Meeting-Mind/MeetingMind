package com.meetingmind.demo.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.demo.dto.KnowledgeGraphResponse;
import com.meetingmind.demo.dto.ai.AiChatResponse;
import com.meetingmind.demo.dto.ai.KnowledgeGraphGatewayRequest;
import com.meetingmind.demo.dto.ai.ProjectAiGatewayChatRequest;
import com.meetingmind.demo.dto.ai.ReportAiGatewayRequest;
import com.meetingmind.demo.dto.ai.ReportAiGatewayResponse;
import com.meetingmind.demo.dto.ai.TaskAiGatewayRequest;
import com.meetingmind.demo.dto.ai.TaskAiGatewayResponse;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HttpAiGatewayClientEndpointTest {

    private static final String SERVICE_TOKEN = "internal-test-token";

    @Test
    void projectGatewayUsesConfiguredBaseUrlAndServiceCredential() throws Exception {
        CapturedRequest captured = new CapturedRequest();
        HttpServer server = jsonServer(
                "/api/internal/project-ai/chat",
                captured,
                "{\"answer\":\"프로젝트 근거입니다.\",\"sources\":[],\"unsupported\":false,"
                        + "\"unsupportedReason\":null,\"model\":\"test\"}"
        );
        try {
            HttpProjectAiGatewayClient client = new HttpProjectAiGatewayClient(
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    baseUrl(server) + "/",
                    SERVICE_TOKEN
            );

            AiChatResponse response = client.chat(new ProjectAiGatewayChatRequest(
                    "space-1",
                    "출시 일정을 알려 주세요.",
                    List.of("meeting-1"),
                    List.of(new ProjectAiGatewayChatRequest.HistoryTurn("USER", "이전 질문"))
            ));

            assertThat(response.answer()).isEqualTo("프로젝트 근거입니다.");
            assertThat(captured.path()).isEqualTo("/api/internal/project-ai/chat");
            assertThat(captured.serviceToken()).isEqualTo(SERVICE_TOKEN);
            assertThat(captured.traceId()).isNotBlank();
            assertThat(captured.body()).contains("\"projectId\":\"space-1\"");
            assertThat(captured.body()).contains("\"allowedMeetingIds\":[\"meeting-1\"]");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void knowledgeGraphGatewayUsesConfiguredBaseUrlAndScope() throws Exception {
        CapturedRequest captured = new CapturedRequest();
        HttpServer server = jsonServer(
                "/api/internal/knowledge/graph",
                captured,
                "{\"clusters\":[],\"edges\":[],\"generatedAt\":\"2026-07-23T00:00:00Z\"}"
        );
        try {
            HttpKnowledgeGraphGatewayClient client = new HttpKnowledgeGraphGatewayClient(
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    baseUrl(server) + "/",
                    SERVICE_TOKEN
            );

            KnowledgeGraphResponse response = client.graph(new KnowledgeGraphGatewayRequest("space-1", List.of("meeting-1")));

            assertThat(response.clusters()).isEmpty();
            assertThat(captured.path()).isEqualTo("/api/internal/knowledge/graph");
            assertThat(captured.serviceToken()).isEqualTo(SERVICE_TOKEN);
            assertThat(captured.traceId()).isNotBlank();
            assertThat(captured.body()).contains("\"projectId\":\"space-1\"");
            assertThat(captured.body()).contains("\"allowedMeetingIds\":[\"meeting-1\"]");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportGatewayUsesConfiguredBaseUrlAndServiceCredential() throws Exception {
        CapturedRequest captured = new CapturedRequest();
        HttpServer server = jsonServer(
                "/api/internal/meeting-ai/generate-report",
                captured,
                "{\"summary\":\"회의 요약\",\"decisions\":[],\"actionItems\":[],\"markdown\":\"# 회의 요약\","
                        + "\"sources\":[],\"unsupported\":false,\"model\":\"test\"}"
        );
        try {
            HttpReportAiGatewayClient client = new HttpReportAiGatewayClient(
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    baseUrl(server) + "/",
                    SERVICE_TOKEN
            );

            ReportAiGatewayResponse response = client.generate(new ReportAiGatewayRequest(
                    "space-1",
                    "meeting-1",
                    "온프레 회의",
                    "markdown",
                    List.of(new ReportAiGatewayRequest.SourceContext(
                            "segment-1", "transcript", "meeting-1", "온프레 회의",
                            "민지", "00:01:00", null, null, "온프레 AI provider를 검증한다."
                    ))
            ));

            assertThat(response.summary()).isEqualTo("회의 요약");
            assertThat(captured.path()).isEqualTo("/api/internal/meeting-ai/generate-report");
            assertThat(captured.serviceToken()).isEqualTo(SERVICE_TOKEN);
            assertThat(captured.traceId()).isNotBlank();
            assertThat(captured.body()).contains("\"meetingId\":\"meeting-1\"");
            assertThat(captured.body()).contains("\"sourceId\":\"segment-1\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void taskGatewayUsesConfiguredBaseUrlAndServiceCredential() throws Exception {
        CapturedRequest captured = new CapturedRequest();
        HttpServer server = jsonServer(
                "/api/internal/meeting-ai/extract-tasks",
                captured,
                "{\"tasks\":[],\"sources\":[],\"unsupported\":false,\"model\":\"test\"}"
        );
        try {
            HttpTaskAiGatewayClient client = new HttpTaskAiGatewayClient(
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    baseUrl(server) + "/",
                    SERVICE_TOKEN
            );

            TaskAiGatewayResponse response = client.extract(new TaskAiGatewayRequest(
                    "space-1",
                    "meeting-1",
                    "온프레 회의",
                    List.of(new TaskAiGatewayRequest.Participant("민지", "QA")),
                    List.of(new TaskAiGatewayRequest.SourceContext(
                            "segment-1", "transcript", "space-1", "meeting-1", "온프레 회의",
                            "민지", "00:01:00", null, null, "QA 마감일을 확인한다."
                    ))
            ));

            assertThat(response.unsupported()).isFalse();
            assertThat(captured.path()).isEqualTo("/api/internal/meeting-ai/extract-tasks");
            assertThat(captured.serviceToken()).isEqualTo(SERVICE_TOKEN);
            assertThat(captured.traceId()).isNotBlank();
            assertThat(captured.body()).contains("\"participants\":[{\"name\":\"민지\",\"role\":\"QA\"}]");
            assertThat(captured.body()).contains("\"projectId\":\"space-1\"");
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer jsonServer(String path, CapturedRequest captured, String responseJson) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            captured.path.set(exchange.getRequestURI().getPath());
            captured.serviceToken.set(exchange.getRequestHeaders().getFirst("X-MeetingMind-Service-Token"));
            captured.traceId.set(exchange.getRequestHeaders().getFirst("X-Request-ID"));
            captured.body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static class CapturedRequest {
        private final AtomicReference<String> path = new AtomicReference<>();
        private final AtomicReference<String> serviceToken = new AtomicReference<>();
        private final AtomicReference<String> traceId = new AtomicReference<>();
        private final AtomicReference<String> body = new AtomicReference<>();

        private String path() {
            return path.get();
        }

        private String serviceToken() {
            return serviceToken.get();
        }

        private String traceId() {
            return traceId.get();
        }

        private String body() {
            return body.get();
        }
    }
}
