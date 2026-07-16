package com.meetingmind.demo.service;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.dto.ai.AiChatResponse;
import com.meetingmind.demo.dto.ai.BackendProjectAiChatRequest;
import com.meetingmind.demo.dto.ai.ProjectAiGatewayChatRequest;
import com.meetingmind.demo.observability.RequestTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProjectAiService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectAiService.class);

    private final AuthService authService;
    private final AiSearchScopeResolver scopeResolver;
    private final ProjectAiGatewayClient aiGatewayClient;

    public ProjectAiService(
            AuthService authService,
            AiSearchScopeResolver scopeResolver,
            ProjectAiGatewayClient aiGatewayClient
    ) {
        this.authService = authService;
        this.scopeResolver = scopeResolver;
        this.aiGatewayClient = aiGatewayClient;
    }

    public AiChatResponse chat(String authorizationHeader, String spaceId, BackendProjectAiChatRequest request) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        AiSearchScopeResolver.ProjectSearchScope scope = scopeResolver.projectScope(user.id(), spaceId);

        long startedAt = System.nanoTime();
        try {
            AiChatResponse response = aiGatewayClient.chat(new ProjectAiGatewayChatRequest(
                    scope.spaceId(),
                    request.question().trim(),
                    scope.allowedMeetingIds()
            ));
            LOGGER.info(
                    "ai_gateway_completed endpoint=project-ai.chat traceId={} durationMs={} sourceCount={} unsupported={} unsupportedReason={}",
                    RequestTrace.currentOrCreate(), elapsedMillis(startedAt), response.sources().size(),
                    response.unsupported(), response.unsupportedReason()
            );
            return response;
        } catch (AiGatewayException exception) {
            LOGGER.warn(
                    "ai_gateway_failed endpoint=project-ai.chat traceId={} durationMs={} errorType=AiGatewayException",
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
}
