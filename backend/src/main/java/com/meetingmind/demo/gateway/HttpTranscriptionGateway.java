package com.meetingmind.demo.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.meetingmind.demo.dto.MeetingDialogueResponse;
import com.meetingmind.demo.dto.stt.ActiveSessionGatewayResponse;
import com.meetingmind.demo.dto.stt.MeetingTranscriptGatewayResponse;
import com.meetingmind.demo.dto.stt.PartialGatewayResponse;
import com.meetingmind.demo.dto.stt.TranscriptionStartGatewayRequest;
import com.meetingmind.demo.dto.stt.TranscriptionStartGatewayResponse;
import com.meetingmind.demo.dto.stt.TranscriptionStatusGatewayResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.demo.config.InternalHttpClientFactory;
import com.meetingmind.demo.observability.RequestTrace;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * STT가 별도 서비스로 분리된 뒤 {@link TranscriptionGateway}의 실제 원격 구현체.
 * {@link InProcessTranscriptionGateway}와 동일한 계약을 내부 HTTP API 호출로 구현한다.
 * transcript()/status()는 아직 Controller가 호출하지 않지만(WorkspaceDomainService가 Core DB를
 * 직접 읽는 동안은), Core STT 코드 제거 이후 그 자리를 대체할 메서드로 남겨둔다.
 */
@Component
public class HttpTranscriptionGateway implements TranscriptionGateway {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    @Autowired
    public HttpTranscriptionGateway(
            ObjectMapper objectMapper,
            InternalHttpClientFactory internalHttpClientFactory,
            @Value("${meetingmind.stt.base-url:http://localhost:8083}") String baseUrl
    ) {
        this(internalHttpClientFactory.newBuilder().build(), objectMapper, baseUrl);
    }

    HttpTranscriptionGateway(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = stripTrailingSlash(baseUrl);
    }

    @Override
    public TranscriptionHandle start(TranscriptionStartCommand command) {
        TranscriptionStartGatewayRequest request = new TranscriptionStartGatewayRequest(
                command.meetingId(), command.roomName(), command.trackId(),
                command.participantDisplayName(),
                command.retentionUntil() == null ? null : command.retentionUntil().toString(),
                command.requestId()
        );
        TranscriptionStartGatewayResponse response;
        try {
            response = post(baseUrl + "/internal/v1/transcriptions", request, TranscriptionStartGatewayResponse.class);
        } catch (TranscriptionGatewayException exception) {
            throw new TranscriptionStartException(null, exception);
        }
        return new TranscriptionHandle(response.sessionId(), response.egressId());
    }

    @Override
    public String activeSessionId(String meetingId) {
        return get(baseUrl + "/internal/v1/meetings/" + meetingId + "/active-session", ActiveSessionGatewayResponse.class)
                .sessionId();
    }

    @Override
    public void stop(String meetingId, String sessionId) {
        String uri = baseUrl + "/internal/v1/transcriptions/" + sessionId + "/stop?meetingId=" + meetingId;
        HttpResponse<String> response = sendRaw(post(uri));
        if (response.statusCode() == 404) {
            throw new TranscriptionSessionNotFoundException(sessionId);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new TranscriptionStopException(
                    sessionId, new TranscriptionGatewayException("STT stop returned " + response.statusCode())
            );
        }
    }

    @Override
    public List<MeetingDialogueResponse.Partial> partials(String meetingId) {
        List<PartialGatewayResponse> partials = getList(
                baseUrl + "/internal/v1/meetings/" + meetingId + "/partials", PartialGatewayResponse.class
        );
        return partials.stream()
                .map(partial -> new MeetingDialogueResponse.Partial(partial.speakerLabel(), null, partial.text()))
                .toList();
    }

    @Override
    public Optional<MeetingTranscriptGatewayResponse> transcript(String meetingId) {
        return getOptional(
                baseUrl + "/internal/v1/meetings/" + meetingId + "/transcript",
                MeetingTranscriptGatewayResponse.class);
    }

    @Override
    public Optional<TranscriptionStatusGatewayResponse> status(String meetingId) {
        return getOptional(
                baseUrl + "/internal/v1/meetings/" + meetingId + "/transcription-status",
                TranscriptionStatusGatewayResponse.class);
    }

    private HttpRequest.Builder post(String uri) {
        return HttpRequest.newBuilder(URI.create(uri))
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.noBody());
    }

    private <T> T post(String uri, Object body, Class<T> responseType) {
        try {
            String json = objectMapper.writeValueAsString(body);
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

    private <T> Optional<T> getOptional(String uri, Class<T> responseType) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri))
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(REQUEST_TIMEOUT)
                .GET();
        applyHeaders(builder);
        HttpResponse<String> response = sendRaw(builder.build());
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new TranscriptionGatewayException(
                    "STT service GET " + uri + " returned " + response.statusCode()
            );
        }
        try {
            return Optional.of(objectMapper.readValue(response.body(), responseType));
        } catch (IOException exception) {
            throw new TranscriptionGatewayException("STT response JSON is invalid.", exception);
        }
    }

    private <T> List<T> getList(String uri, Class<T> elementType) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri))
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(REQUEST_TIMEOUT)
                .GET();
        applyHeaders(builder);
        HttpResponse<String> response = sendRaw(builder);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new TranscriptionGatewayException(
                    "STT service GET " + uri + " returned " + response.statusCode()
            );
        }
        try {
            return objectMapper.readValue(
                    response.body(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementType)
            );
        } catch (IOException exception) {
            throw new TranscriptionGatewayException("STT response JSON is invalid.", exception);
        }
    }

    private <T> T send(HttpRequest request, Class<T> responseType) {
        HttpResponse<String> response = sendRaw(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new TranscriptionGatewayException(
                    "STT service " + request.method() + " " + request.uri() + " returned " + response.statusCode()
            );
        }
        if (responseType == Void.class || response.body() == null || response.body().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(response.body(), responseType);
        } catch (IOException exception) {
            throw new TranscriptionGatewayException("STT response JSON is invalid.", exception);
        }
    }

    private HttpResponse<String> sendRaw(HttpRequest.Builder builder) {
        applyHeaders(builder);
        return sendRaw(builder.build());
    }

    private HttpResponse<String> sendRaw(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new TranscriptionGatewayException("STT service is unavailable.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TranscriptionGatewayException("STT service request was interrupted.", exception);
        }
    }

    private void applyHeaders(HttpRequest.Builder builder) {
        builder.header(RequestTrace.HEADER_NAME, RequestTrace.currentOrCreate());
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8083";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
