package com.meetingmind.demo.service;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.dto.ai.AiChatResponse;
import com.meetingmind.demo.dto.ai.BackendMeetingAiChatRequest;
import com.meetingmind.demo.dto.ai.MeetingAiGatewayChatRequest;
import com.meetingmind.demo.observability.RequestTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MeetingAiService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MeetingAiService.class);

    private final AuthService authService;
    private final AiSearchScopeResolver scopeResolver;
    private final MeetingAiGatewayClient aiGatewayClient;
    private final WorkspaceDomainService workspaceDomainService;

    public MeetingAiService(
            AuthService authService,
            AiSearchScopeResolver scopeResolver,
            MeetingAiGatewayClient aiGatewayClient,
            WorkspaceDomainService workspaceDomainService
    ) {
        this.authService = authService;
        this.scopeResolver = scopeResolver;
        this.aiGatewayClient = aiGatewayClient;
        this.workspaceDomainService = workspaceDomainService;
    }

    public AiChatResponse chat(String authorizationHeader, String meetingId, BackendMeetingAiChatRequest request) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        AiSearchScopeResolver.MeetingSearchScope scope = scopeResolver.meetingScope(user.id(), meetingId);

        long startedAt = System.nanoTime();
        try {
            AiChatResponse response = aiGatewayClient.chat(new MeetingAiGatewayChatRequest(
                    scope.spaceId(),
                    scope.meetingId(),
                    request.question().trim()
            ));
            recordUsage(user.id(), scope, response);
            LOGGER.info(
                    "ai_gateway_completed endpoint=meeting-ai.chat traceId={} durationMs={} sourceCount={} unsupported={} unsupportedReason={}",
                    RequestTrace.currentOrCreate(), elapsedMillis(startedAt), response.sources().size(),
                    response.unsupported(), response.unsupportedReason()
            );
            return response;
        } catch (AiGatewayException exception) {
            LOGGER.warn(
                    "ai_gateway_failed endpoint=meeting-ai.chat traceId={} durationMs={} errorType=AiGatewayException",
                    RequestTrace.currentOrCreate(), elapsedMillis(startedAt)
            );
            throw new AuthorizationException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_PROVIDER_UNAVAILABLE",
                    "AI provider 응답을 받을 수 없습니다."
            );
        }
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private void recordUsage(
            String actorUserId,
            AiSearchScopeResolver.MeetingSearchScope scope,
            AiChatResponse response
    ) {
        AiChatResponse.AiUsageMetrics usage = response.usage();
        if (usage == null) {
            return;
        }
        workspaceDomainService.recordAiUsageEvent(
                actorUserId,
                scope.spaceId(),
                scope.meetingId(),
                "meeting-ai",
                usage.provider(),
                usage.apiStyle(),
                usage.stream(),
                usage.inputTokens(),
                usage.outputTokens(),
                totalTokens(usage),
                (long) usage.totalMs()
        );
    }

    private Integer totalTokens(AiChatResponse.AiUsageMetrics usage) {
        Integer inputTokens = usage.inputTokens();
        Integer outputTokens = usage.outputTokens();
        if (inputTokens != null && outputTokens != null) {
            return inputTokens + outputTokens;
        }
        if (inputTokens != null && usage.outputTokenEstimate() != null) {
            return inputTokens + usage.outputTokenEstimate();
        }
        if (outputTokens != null) {
            return outputTokens;
        }
        return inputTokens;
    }
}
