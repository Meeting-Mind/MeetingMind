package com.meetingmind.demo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.demo.config.InternalHttpClientFactory;
import com.meetingmind.demo.dto.ai.TaskAiGatewayRequest;
import com.meetingmind.demo.dto.ai.TaskAiGatewayResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
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
    private final AiGatewayGuard guard;

    @Autowired
    public HttpTaskAiGatewayClient(
            ObjectMapper objectMapper,
            InternalHttpClientFactory internalHttpClientFactory,
            @Value("${meetingmind.ai.base-url:http://localhost:8000}") String aiBaseUrl,
            @Value("${meetingmind.ai.service-token:}") String serviceToken,
            @Value("${meetingmind.ai.guard.max-concurrent:16}") int maxConcurrent,
            @Value("${meetingmind.ai.guard.failure-threshold:3}") int failureThreshold,
            @Value("${meetingmind.ai.guard.open-duration:30s}") Duration openDuration
    ) {
this(
                internalHttpClientFactory.newBuilder().build(),
                objectMapper,
                aiBaseUrl,
                serviceToken,
                new AiGatewayGuardPolicy(maxConcurrent, failureThreshold, openDuration),
                Clock.systemUTC()
        );
    }

    HttpTaskAiGatewayClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String aiBaseUrl,
            String serviceToken
    ) {
        this(httpClient, objectMapper, aiBaseUrl, serviceToken, new AiGatewayGuardPolicy(16, 3, Duration.ofSeconds(30)), Clock.systemUTC());
    }

    HttpTaskAiGatewayClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String aiBaseUrl,
            String serviceToken,
            AiGatewayGuardPolicy guardPolicy,
            Clock clock
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.extractUri = URI.create(stripTrailingSlash(aiBaseUrl) + "/api/internal/meeting-ai/extract-tasks");
        this.serviceToken = serviceToken == null ? "" : serviceToken;
        this.guard = new AiGatewayGuard(guardPolicy, clock);
    }

    @Override
    public TaskAiGatewayResponse extract(TaskAiGatewayRequest request) {
        try {
            return guard.execute(() -> {
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
            });
        } catch (AiGatewayGuardRejectedException exception) {
            throw new AiGatewayException("AI provider is temporarily unavailable.", exception);
        }
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8000";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
