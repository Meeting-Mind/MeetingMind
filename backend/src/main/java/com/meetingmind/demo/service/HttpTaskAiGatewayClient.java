package com.meetingmind.demo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.demo.dto.ai.TaskAiGatewayRequest;
import com.meetingmind.demo.dto.ai.TaskAiGatewayResponse;
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
public class HttpTaskAiGatewayClient implements TaskAiGatewayClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI extractUri;
    private final String serviceToken;

    @Autowired
    public HttpTaskAiGatewayClient(
            ObjectMapper objectMapper,
            @Value("${meetingmind.ai.base-url:http://localhost:8000}") String aiBaseUrl,
            @Value("${meetingmind.ai.service-token:}") String serviceToken
    ) {
        this(HttpClient.newHttpClient(), objectMapper, aiBaseUrl, serviceToken);
    }

    HttpTaskAiGatewayClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String aiBaseUrl,
            String serviceToken
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.extractUri = URI.create(stripTrailingSlash(aiBaseUrl) + "/api/internal/meeting-ai/extract-tasks");
        this.serviceToken = serviceToken == null ? "" : serviceToken;
    }

    @Override
    public TaskAiGatewayResponse extract(TaskAiGatewayRequest request) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(extractUri)
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)));
            AiGatewayRequestHeaders.applyServiceToken(requestBuilder, serviceToken);
            HttpRequest httpRequest = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiGatewayException("AI provider returned " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), TaskAiGatewayResponse.class);
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
