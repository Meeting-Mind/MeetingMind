package com.meetingmind.demo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.demo.config.InternalHttpClientFactory;
import com.meetingmind.demo.dto.ai.AiChatResponse;
import com.meetingmind.demo.dto.ai.ReportAiGatewayRequest;
import com.meetingmind.demo.dto.ai.ReportAiGatewayResponse;
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
public class HttpReportAiGatewayClient implements ReportAiGatewayClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI generateUri;
    private final String serviceToken;
    private final AiGatewayGuard guard;

    @Autowired
    public HttpReportAiGatewayClient(
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

    HttpReportAiGatewayClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String aiBaseUrl,
            String serviceToken
    ) {
        this(httpClient, objectMapper, aiBaseUrl, serviceToken, new AiGatewayGuardPolicy(16, 3, Duration.ofSeconds(30)), Clock.systemUTC());
    }

    HttpReportAiGatewayClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String aiBaseUrl,
            String serviceToken,
            AiGatewayGuardPolicy guardPolicy,
            Clock clock
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.generateUri = URI.create(stripTrailingSlash(aiBaseUrl) + "/api/internal/meeting-ai/generate-report");
        this.serviceToken = serviceToken == null ? "" : serviceToken;
        this.guard = new AiGatewayGuard(guardPolicy, clock);
    }

    @Override
    public ReportAiGatewayResponse generate(ReportAiGatewayRequest request) {
        try {
            return guard.execute(() -> {
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
                    return parseResponse(response.body());
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

    private ReportAiGatewayResponse parseResponse(String responseBody) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode summary = root.get("summary");
        if (summary != null && summary.isArray()) {
            ReportAiGatewayResponse response = objectMapper.treeToValue(root, ReportAiGatewayResponse.class);
            if (response.schemaVersion() != 0 && response.schemaVersion() != 2) {
                throw new AiGatewayException("AI report response schema version is unsupported.");
            }
            if (response.schemaVersion() == 2) {
                return response;
            }
            return new ReportAiGatewayResponse(
                    2,
                    response.summary(),
                    response.decisions(),
                    response.actionItems(),
                    response.sources(),
                    response.droppedCount(),
                    response.unsupported(),
                    response.unsupportedReason(),
                    response.model(),
                    response.usage()
            );
        }

        LegacyReportAiGatewayResponse legacy = objectMapper.treeToValue(root, LegacyReportAiGatewayResponse.class);
        var sources = legacy.sources() == null ? java.util.List.<ReportAiGatewayResponse.Source>of() : legacy.sources();
        var sourceIds = sources.stream()
                .map(ReportAiGatewayResponse.Source::sourceId)
                .filter(HttpReportAiGatewayClient::hasText)
                .distinct()
                .toList();
        var summaryRows = hasText(legacy.summary()) && !sourceIds.isEmpty()
                ? java.util.List.of(new ReportAiGatewayResponse.SummarySentence(legacy.summary().trim(), sourceIds))
                : java.util.List.<ReportAiGatewayResponse.SummarySentence>of();
        String unsupportedReason = legacy.unsupported()
                ? (hasText(legacy.unsupportedReason())
                        ? legacy.unsupportedReason()
                        : (sources.isEmpty() ? "NO_EVIDENCE" : "MODEL_UNSUPPORTED"))
                : null;
        return new ReportAiGatewayResponse(
                1,
                summaryRows,
                legacy.decisions(),
                legacy.actionItems(),
                sources,
                0,
                legacy.unsupported(),
                unsupportedReason,
                legacy.model(),
                legacy.usage()
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record LegacyReportAiGatewayResponse(
            String summary,
            java.util.List<ReportAiGatewayResponse.Decision> decisions,
            java.util.List<ReportAiGatewayResponse.ActionItem> actionItems,
            String markdown,
            java.util.List<ReportAiGatewayResponse.Source> sources,
            boolean unsupported,
            String unsupportedReason,
            String model,
            AiChatResponse.AiUsageMetrics usage
    ) {
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8000";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
