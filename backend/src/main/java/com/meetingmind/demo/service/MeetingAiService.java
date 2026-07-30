package com.meetingmind.demo.service;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.dto.ai.AiChatResponse;
import com.meetingmind.demo.dto.ai.BackendMeetingAiChatRequest;
import com.meetingmind.demo.dto.ai.MeetingAiGatewayChatRequest;
import com.meetingmind.demo.observability.RequestTrace;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MeetingAiService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MeetingAiService.class);
    private static final int HISTORY_CONTEXT_LIMIT = 10;

    private final AuthService authService;
    private final AiSearchScopeResolver scopeResolver;
    private final MeetingAiGatewayClient aiGatewayClient;
    private final MeetingAiHistoryStore historyStore;
    private final WorkspaceDomainService workspaceDomainService;
    private final Clock clock;

    @Autowired
    public MeetingAiService(
            AuthService authService,
            AiSearchScopeResolver scopeResolver,
            MeetingAiGatewayClient aiGatewayClient,
            MeetingAiHistoryStore historyStore,
            WorkspaceDomainService workspaceDomainService
    ) {
        this(authService, scopeResolver, aiGatewayClient, historyStore, workspaceDomainService, Clock.systemUTC());
    }

    public MeetingAiService(
            AuthService authService,
            AiSearchScopeResolver scopeResolver,
            MeetingAiGatewayClient aiGatewayClient,
            WorkspaceDomainService workspaceDomainService
    ) {
        this(
                authService,
                scopeResolver,
                aiGatewayClient,
                new InMemoryMeetingAiHistoryStore(),
                workspaceDomainService,
                Clock.systemUTC()
        );
    }

    public MeetingAiService(
            AuthService authService,
            AiSearchScopeResolver scopeResolver,
            MeetingAiGatewayClient aiGatewayClient,
            MeetingAiHistoryStore historyStore,
            WorkspaceDomainService workspaceDomainService,
            Clock clock
    ) {
        this.authService = authService;
        this.scopeResolver = scopeResolver;
        this.aiGatewayClient = aiGatewayClient;
        this.historyStore = historyStore;
        this.workspaceDomainService = workspaceDomainService;
        this.clock = clock;
    }

    public AiChatResponse chat(String authorizationHeader, String meetingId, BackendMeetingAiChatRequest request) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        AiSearchScopeResolver.MeetingSearchScope scope = scopeResolver.meetingScope(user.id(), meetingId);

        long startedAt = System.nanoTime();
        try {
            List<MeetingAiGatewayChatRequest.HistoryTurn> history = historyStore
                    .find(scope.meetingId(), user.id(), HISTORY_CONTEXT_LIMIT).stream()
                    .map(message -> new MeetingAiGatewayChatRequest.HistoryTurn(message.role(), message.content()))
                    .toList();
            AiChatResponse response = aiGatewayClient.chat(new MeetingAiGatewayChatRequest(
                    scope.spaceId(),
                    scope.meetingId(),
                    request.question().trim(),
                    history
            ));
            recordUsage(user.id(), scope, response);
            LOGGER.info(
                    "ai_gateway_completed endpoint=meeting-ai.chat traceId={} durationMs={} sourceCount={} unsupported={} unsupportedReason={}",
                    RequestTrace.currentOrCreate(), elapsedMillis(startedAt), response.sources().size(),
                    response.unsupported(), response.unsupportedReason()
            );
            Instant now = Instant.now(clock);
            historyStore.append(scope.meetingId(), user.id(), "USER", request.question().trim(), now);
            historyStore.append(scope.meetingId(), user.id(), "ASSISTANT", response.answer(), now.plusMillis(1));
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
