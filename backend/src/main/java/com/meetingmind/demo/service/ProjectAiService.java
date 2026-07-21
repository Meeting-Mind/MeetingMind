package com.meetingmind.demo.service;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.dto.ai.AiChatResponse;
import com.meetingmind.demo.dto.ai.BackendProjectAiChatRequest;
import com.meetingmind.demo.dto.ai.ProjectAiGatewayChatRequest;
import com.meetingmind.demo.domain.ProjectAiMessage;
import com.meetingmind.demo.observability.RequestTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class ProjectAiService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectAiService.class);
    private static final int HISTORY_CONTEXT_LIMIT = 10;

    private final AuthService authService;
    private final AiSearchScopeResolver scopeResolver;
    private final ProjectAiGatewayClient aiGatewayClient;
    private final ProjectAiHistoryStore historyStore;
    private final Clock clock;

    @Autowired
    public ProjectAiService(
            AuthService authService,
            AiSearchScopeResolver scopeResolver,
            ProjectAiGatewayClient aiGatewayClient,
            ProjectAiHistoryStore historyStore
    ) {
        this.authService = authService;
        this.scopeResolver = scopeResolver;
        this.aiGatewayClient = aiGatewayClient;
        this.historyStore = historyStore;
        this.clock = Clock.systemUTC();
    }

    public ProjectAiService(AuthService authService, AiSearchScopeResolver scopeResolver, ProjectAiGatewayClient aiGatewayClient) {
        this(authService, scopeResolver, aiGatewayClient, new InMemoryProjectAiHistoryStore());
    }

    public AiChatResponse chat(String authorizationHeader, String spaceId, BackendProjectAiChatRequest request) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        AiSearchScopeResolver.ProjectSearchScope scope = scopeResolver.projectScope(user.id(), spaceId);

        long startedAt = System.nanoTime();
        try {
            AiChatResponse response = aiGatewayClient.chat(new ProjectAiGatewayChatRequest(
                    scope.spaceId(),
                    request.question().trim(),
                    scope.allowedMeetingIds(),
                    historyStore.find(scope.spaceId(), user.id(), HISTORY_CONTEXT_LIMIT).stream()
                            .map(message -> new ProjectAiGatewayChatRequest.HistoryTurn(message.role(), message.content()))
                            .toList()
            ));
            LOGGER.info(
                    "ai_gateway_completed endpoint=project-ai.chat traceId={} durationMs={} sourceCount={} unsupported={} unsupportedReason={}",
                    RequestTrace.currentOrCreate(), elapsedMillis(startedAt), response.sources().size(),
                    response.unsupported(), response.unsupportedReason()
            );
            Instant now = Instant.now(clock);
            historyStore.append(scope.spaceId(), user.id(), "USER", request.question().trim(), now);
            historyStore.append(scope.spaceId(), user.id(), "ASSISTANT", response.answer(), now);
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

    public List<ProjectAiMessage> history(String authorizationHeader, String spaceId) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        scopeResolver.projectScope(user.id(), spaceId);
        return historyStore.find(spaceId, user.id(), 50);
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }
}
