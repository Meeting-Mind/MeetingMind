package com.meetingmind.demo.service;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.domain.DomainTerm;
import com.meetingmind.demo.domain.DomainTermStore;
import com.meetingmind.demo.domain.SharedGlossaryStore;
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
    private final SharedGlossaryStore sharedGlossaryStore;
    private final MeetingAiGatewayClient aiGatewayClient;

    public MeetingTermExplanationService(
            AuthService authService,
            AiSearchScopeResolver scopeResolver,
            DomainTermStore domainTermStore,
            SharedGlossaryStore sharedGlossaryStore,
            MeetingAiGatewayClient aiGatewayClient
    ) {
        this.authService = authService;
        this.scopeResolver = scopeResolver;
        this.domainTermStore = domainTermStore;
        this.sharedGlossaryStore = sharedGlossaryStore;
        this.aiGatewayClient = aiGatewayClient;
    }

    @Transactional(readOnly = true)
    public TermExplanationResponse explain(String authorizationHeader, String meetingId, String rawTerm) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        AiSearchScopeResolver.MeetingSearchScope scope = scopeResolver.meetingScope(user.id(), meetingId);
        String term = rawTerm.trim();

        String normalizedTerm = normalize(term);

        // Space가 직접 등록한 정의를 공용 사전보다 먼저 본다. 같은 용어를 조직 맥락에 맞게 덮어쓸 수 있어야 한다.
        DomainTerm dictionaryTerm = domainTermStore.findActiveExact(scope.spaceId(), normalizedTerm).orElse(null);
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

        // 구독하지 않은 분야는 조회 단계에서 제외되므로 AI 컨텍스트로도 넘어가지 않는다.
        SharedGlossaryStore.SharedGlossaryMatch sharedTerm =
                sharedGlossaryStore.findSubscribedActiveExact(scope.spaceId(), normalizedTerm).orElse(null);
        if (sharedTerm != null) {
            return new TermExplanationResponse(
                    term,
                    sharedTerm.definition(),
                    "glossary",
                    List.of(new AiSource(sharedTerm.termId(), "glossary", sharedTerm.categoryName(), sharedTerm.definition())),
                    false,
                    null,
                    "shared-glossary"
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
