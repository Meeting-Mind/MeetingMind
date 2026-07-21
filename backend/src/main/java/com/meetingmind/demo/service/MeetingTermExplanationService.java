package com.meetingmind.demo.service;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.domain.DomainTerm;
import com.meetingmind.demo.domain.DomainTermStore;
import com.meetingmind.demo.dto.ai.AiSource;
import com.meetingmind.demo.dto.ai.MeetingAiGatewayTermRequest;
import com.meetingmind.demo.dto.ai.TermExplanationResponse;
import com.meetingmind.demo.observability.RequestTrace;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetingTermExplanationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MeetingTermExplanationService.class);

    private final AuthService authService;
    private final AiSearchScopeResolver scopeResolver;
    private final DomainTermStore domainTermStore;
    private final MeetingAiGatewayClient aiGatewayClient;

    public MeetingTermExplanationService(
            AuthService authService,
            AiSearchScopeResolver scopeResolver,
            DomainTermStore domainTermStore,
            MeetingAiGatewayClient aiGatewayClient
    ) {
        this.authService = authService;
        this.scopeResolver = scopeResolver;
        this.domainTermStore = domainTermStore;
        this.aiGatewayClient = aiGatewayClient;
    }

    @Transactional(readOnly = true)
    public TermExplanationResponse explain(String authorizationHeader, String meetingId, String rawTerm) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        AiSearchScopeResolver.MeetingSearchScope scope = scopeResolver.meetingScope(user.id(), meetingId);
        String term = rawTerm.trim();

        DomainTerm dictionaryTerm = domainTermStore.findActiveExact(scope.spaceId(), normalize(term)).orElse(null);
        if (dictionaryTerm != null) {
            return new TermExplanationResponse(
                    term,
                    dictionaryTerm.definition(),
                    "glossary",
                    List.of(new AiSource(dictionaryTerm.id(), "glossary", "Domain Dictionary", dictionaryTerm.definition())),
                    false,
                    null,
                    "local-glossary"
            );
        }

        long startedAt = System.nanoTime();
        try {
            TermExplanationResponse response = aiGatewayClient.explainTerm(new MeetingAiGatewayTermRequest(
                    scope.spaceId(),
                    scope.meetingId(),
                    term
            ));
            LOGGER.info(
                    "ai_gateway_completed endpoint=meeting-ai.explain-term traceId={} durationMs={} sourceCount={} unsupported={} unsupportedReason={}",
                    RequestTrace.currentOrCreate(), elapsedMillis(startedAt), response.sources().size(),
                    response.unsupported(), response.unsupportedReason()
            );
            return response;
        } catch (AiGatewayException exception) {
            LOGGER.warn(
                    "ai_gateway_failed endpoint=meeting-ai.explain-term traceId={} durationMs={} errorType=AiGatewayException",
                    RequestTrace.currentOrCreate(), elapsedMillis(startedAt)
            );
            throw new AuthorizationException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_PROVIDER_UNAVAILABLE",
                    "AI provider 응답을 받을 수 없습니다."
            );
        }
    }

    private static String normalize(String term) {
        return term.toLowerCase(Locale.ROOT);
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }
}
