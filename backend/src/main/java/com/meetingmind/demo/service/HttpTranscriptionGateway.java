package com.meetingmind.demo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.demo.dto.stt.MeetingTranscriptGatewayResponse;
import com.meetingmind.demo.dto.stt.TranscriptionStartGatewayRequest;
import com.meetingmind.demo.dto.stt.TranscriptionStartGatewayResponse;
import com.meetingmind.demo.dto.stt.TranscriptionStatusGatewayResponse;
import com.meetingmind.demo.observability.RequestTrace;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HttpTranscriptionGateway implements TranscriptionGateway {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final String SERVICE_TOKEN_HEADER = "X-MeetingMind-Service-Token";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String serviceToken;

    @Autowired
    public HttpTranscriptionGateway(
            ObjectMapper objectMapper,
            @Value("${meetingmind.stt.base-url:http://localhost:8081}") String baseUrl,
            @Value("${meetingmind.stt.service-token:}") String serviceToken
    ) {
        this(HttpClient.newHttpClient(), objectMapper, baseUrl, serviceToken);
    }

    HttpTranscriptionGateway(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl, String serviceToken) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.serviceToken = serviceToken == null ? "" : serviceToken;
    }

    @Override
    public TranscriptionStartGatewayResponse start(TranscriptionStartGatewayRequest request) {
        return post(baseUrl + "/internal/v1/transcriptions", request, TranscriptionStartGatewayResponse.class);
    }

    @Override
    public void stop(String sessionId) {
        post(baseUrl + "/internal/v1/transcriptions/" + sessionId + "/stop", null, Void.class);
    }

    @Override
    public MeetingTranscriptGatewayResponse transcript(String meetingId) {
        return get(baseUrl + "/internal/v1/meetings/" + meetingId + "/transcript", MeetingTranscriptGatewayResponse.class);
    }

    @Override
    public TranscriptionStatusGatewayResponse status(String meetingId) {
        return get(baseUrl + "/internal/v1/meetings/" + meetingId + "/transcription-status", TranscriptionStatusGatewayResponse.class);
    }

    private <T> T post(String uri, Object body, Class<T> responseType) {
        try {
            String json = body == null ? "" : objectMapper.writeValueAsString(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            applyHeaders(builder);
            return send(builder.build(), responseType);
        } catch (JsonProcessingException exception) {
            throw new TranscriptionGatewayException("STT request JSON is invalid.", exception);
        }
    }

    private <T> T get(String uri, Class<T> responseType) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri))
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(REQUEST_TIMEOUT)
                .GET();
        applyHeaders(builder);
        return send(builder.build(), responseType);
    }

    private <T> T send(HttpRequest request, Class<T> responseType) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new TranscriptionGatewayException(
                        "STT service " + request.method() + " " + request.uri() + " returned " + response.statusCode()
                );
            }
            if (responseType == Void.class || response.body() == null || response.body().isBlank()) {
                return null;
            }
            return objectMapper.readValue(response.body(), responseType);
        } catch (IOException exception) {
            throw new TranscriptionGatewayException("STT service is unavailable.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TranscriptionGatewayException("STT service request was interrupted.", exception);
        }
    }

    private void applyHeaders(HttpRequest.Builder builder) {
        if (!serviceToken.isBlank()) {
            builder.header(SERVICE_TOKEN_HEADER, serviceToken);
        }
        builder.header(RequestTrace.HEADER_NAME, RequestTrace.currentOrCreate());
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8081";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
