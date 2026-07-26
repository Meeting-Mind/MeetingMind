package com.meetingmind.bff.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.bff.auth.BffTokenManager;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
public class AiUsageRecorder {

    private static final Logger log = LoggerFactory.getLogger(AiUsageRecorder.class);
    private static final String INTERNAL_AI_USAGE_PATH = "/api/v1/internal/ai-usage/events";

    private final DownstreamHttpClient downstreamClient;
    private final BffTokenManager tokenManager;
    private final ObjectMapper objectMapper;

    public AiUsageRecorder(
            DownstreamHttpClient downstreamClient,
            BffTokenManager tokenManager,
            ObjectMapper objectMapper
    ) {
        this.downstreamClient = downstreamClient;
        this.tokenManager = tokenManager;
        this.objectMapper = objectMapper;
    }

    public void recordIfPresent(HttpServletRequest request, ProxyRoute route, ProxyResponse response) {
        if (route.service() != DownstreamService.AI || !response.status().is2xxSuccessful()) {
            return;
        }
        AiUsageRecordPayload payload = extractPayload(request.getRequestURI(), response.body());
        if (payload == null) {
            return;
        }
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("feature", payload.feature());
            requestBody.put("streamed", payload.streamed());
            if (payload.spaceId() != null) requestBody.put("spaceId", payload.spaceId());
            if (payload.meetingId() != null) requestBody.put("meetingId", payload.meetingId());
            if (payload.provider() != null) requestBody.put("provider", payload.provider());
            if (payload.apiStyle() != null) requestBody.put("apiStyle", payload.apiStyle());
            if (payload.inputTokens() != null) requestBody.put("inputTokens", payload.inputTokens());
            if (payload.outputTokens() != null) requestBody.put("outputTokens", payload.outputTokens());
            if (payload.totalTokens() != null) requestBody.put("totalTokens", payload.totalTokens());
            if (payload.totalMs() != null) requestBody.put("totalMs", payload.totalMs());
            byte[] body = objectMapper.writeValueAsBytes(requestBody);
            ProxyRequest proxyRequest = new ProxyRequest(
                    HttpMethod.POST,
                    INTERNAL_AI_USAGE_PATH,
                    null,
                    "application/json",
                    "application/json",
                    body
            );
            tokenManager.execute(
                    request,
                    DownstreamService.CORE.audience(),
                    authorization -> downstreamClient.execute(DownstreamService.CORE, proxyRequest, authorization)
            );
        } catch (Exception exception) {
            log.warn("AI usage event recording failed: path={}", request.getRequestURI(), exception);
        }
    }

    private AiUsageRecordPayload extractPayload(String requestPath, byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode usage = root == null ? null : root.get("usage");
            if (usage == null || usage.isNull() || usage.isMissingNode()) {
                return null;
            }
            return new AiUsageRecordPayload(
                    extractSpaceId(requestPath),
                    extractMeetingId(requestPath),
                    featureForRoute(requestPath),
                    textValue(usage, "provider"),
                    textValue(usage, "apiStyle"),
                    booleanValue(usage, "stream"),
                    intValue(usage, "inputTokens"),
                    intValue(usage, "outputTokens"),
                    intValue(usage, "totalTokens"),
                    longValue(usage, "totalMs")
            );
        } catch (Exception exception) {
            log.debug("AI usage parsing skipped: path={}", requestPath, exception);
            return null;
        }
    }

    private String featureForRoute(String value) {
        if (value.contains("/spaces/") && value.contains("/ai/chat")) {
            return "project-ai";
        }
        if (value.contains("/reports/generate") || value.contains("/ai-edits") || value.contains("/task-candidates/generate")) {
            return "report-ai";
        }
        return "meeting-ai";
    }

    private String extractSpaceId(String value) {
        if (!value.contains("/spaces/")) {
            return null;
        }
        String[] parts = value.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("spaces".equals(parts[i])) {
                return parts[i + 1].replace("^", "").replace("$", "");
            }
        }
        return null;
    }

    private String extractMeetingId(String value) {
        if (!value.contains("/meetings/")) {
            return null;
        }
        String[] parts = value.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("meetings".equals(parts[i])) {
                return parts[i + 1].replace("^", "").replace("$", "");
            }
        }
        return null;
    }

    private String textValue(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }

    private boolean booleanValue(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value != null && !value.isNull() && value.asBoolean(false);
    }

    private Integer intValue(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private Long longValue(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private record AiUsageRecordPayload(
            String spaceId,
            String meetingId,
            String feature,
            String provider,
            String apiStyle,
            boolean streamed,
            Integer inputTokens,
            Integer outputTokens,
            Integer totalTokens,
            Long totalMs
    ) {
    }
}
