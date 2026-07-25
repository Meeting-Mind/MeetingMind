package com.meetingmind.demo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.demo.config.InternalHttpClientFactory;
import com.meetingmind.demo.dto.ai.ReportAiGatewayRequest;
import com.meetingmind.demo.dto.ai.ReportAiGatewayResponse;
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
public class HttpReportAiGatewayClient implements ReportAiGatewayClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI generateUri;
    private final String serviceToken;

    @Autowired
    public HttpReportAiGatewayClient(
            ObjectMapper objectMapper,
            InternalHttpClientFactory internalHttpClientFactory,
            @Value("${meetingmind.ai.base-url:http://localhost:8000}") String aiBaseUrl,
            @Value("${meetingmind.ai.service-token:}") String serviceToken
    ) {
        this(internalHttpClientFactory.newBuilder().build(), objectMapper, aiBaseUrl, serviceToken);
    }

    HttpReportAiGatewayClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String aiBaseUrl,
            String serviceToken
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.generateUri = URI.create(stripTrailingSlash(aiBaseUrl) + "/api/internal/meeting-ai/generate-report");
        this.serviceToken = serviceToken == null ? "" : serviceToken;
    }

    @Override
    public ReportAiGatewayResponse generate(ReportAiGatewayRequest request) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(generateUri)
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
            return objectMapper.readValue(response.body(), ReportAiGatewayResponse.class);
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
