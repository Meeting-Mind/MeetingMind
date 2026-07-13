package com.meetingmind.demo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.demo.dto.ai.AiChatResponse;
import com.meetingmind.demo.dto.ai.ProjectAiGatewayChatRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HttpProjectAiGatewayClient implements ProjectAiGatewayClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI chatUri;

    @Autowired
    public HttpProjectAiGatewayClient(
            ObjectMapper objectMapper,
            @Value("${meetingmind.ai.base-url:http://localhost:8000}") String aiBaseUrl
    ) {
        this(HttpClient.newHttpClient(), objectMapper, aiBaseUrl);
    }

    HttpProjectAiGatewayClient(HttpClient httpClient, ObjectMapper objectMapper, String aiBaseUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.chatUri = URI.create(stripTrailingSlash(aiBaseUrl) + "/api/internal/project-ai/chat");
    }

    @Override
    public AiChatResponse chat(ProjectAiGatewayChatRequest request) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(chatUri)
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiGatewayException("AI provider returned " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), AiChatResponse.class);
        } catch (JsonProcessingException exception) {
            throw new AiGatewayException("AI request or response JSON is invalid.", exception);
        } catch (IOException exception) {
            throw new AiGatewayException("AI provider is unavailable.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiGatewayException("AI provider request was interrupted.", exception);
        }
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8000";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
