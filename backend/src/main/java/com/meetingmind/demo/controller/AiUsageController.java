package com.meetingmind.demo.controller;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.dto.RecordAiUsageEventRequest;
import com.meetingmind.demo.dto.RecordAiUsageEventResponse;
import com.meetingmind.demo.dto.SpaceAiUsageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AiUsageController {

    private final AuthService authService;
    private final WorkspaceDomainService workspaceDomainService;
    private final int tokenQuota;

    public AiUsageController(
            AuthService authService,
            WorkspaceDomainService workspaceDomainService,
            @Value("${meetingmind.ai.token-quota:0}") int tokenQuota
    ) {
        this.authService = authService;
        this.workspaceDomainService = workspaceDomainService;
        this.tokenQuota = tokenQuota;
    }

    /**
     * quota는 표시 전용이다. 초과해도 AI 호출을 차단하지 않는다. 프로토타입에서 실수로 팀
     * 전체가 막히는 위험을 지지 않기 위한 결정이며, 차단이 필요해지면 별도 과제로 다룬다.
     *
     * <p>quota가 설정되지 않았으면 {@code limit}과 {@code usagePercent}를 모두 null로 둔다.
     * 0을 내려보내면 클라이언트가 "한도 0"으로 오해할 수 있다.
     */
    private Integer quotaLimit() {
        return tokenQuota > 0 ? tokenQuota : null;
    }

    private Double usagePercent(int totalInputTokens, int totalOutputTokens) {
        Integer limit = quotaLimit();
        if (limit == null) {
            return null;
        }
        long usedTokens = (long) totalInputTokens + (long) totalOutputTokens;
        return Math.round(usedTokens * 10_000.0 / limit) / 100.0;
    }

    @GetMapping("/spaces/{spaceId}/ai/usage")
    public SpaceAiUsageResponse spaceAiUsage(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @RequestParam(defaultValue = "month") String window
    ) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        WorkspaceDomainService.SpaceAiUsage usage = workspaceDomainService.spaceAiUsage(user.id(), spaceId, window);
        return new SpaceAiUsageResponse(
                usage.window(),
                quotaLimit(),
                usage.totalRequests(),
                usage.totalInputTokens(),
                usage.totalOutputTokens(),
                usagePercent(usage.totalInputTokens(), usage.totalOutputTokens()),
                usage.features().stream()
                        .map(feature -> new SpaceAiUsageResponse.FeatureUsage(
                                feature.feature(),
                                feature.requests(),
                                feature.inputTokens(),
                                feature.outputTokens()
                        ))
                        .toList()
        );
    }

    @PostMapping("/internal/ai-usage/events")
    public RecordAiUsageEventResponse recordAiUsageEvent(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody RecordAiUsageEventRequest request
    ) {
        AuthUserResponse user = authService.currentUser(authorizationHeader);
        WorkspaceDomainService.RecordedAiUsage usage = workspaceDomainService.recordAiUsageEvent(
                user.id(),
                request.spaceId(),
                request.meetingId(),
                request.feature(),
                request.provider(),
                request.apiStyle(),
                Boolean.TRUE.equals(request.streamed()),
                request.inputTokens(),
                request.outputTokens(),
                request.totalTokens(),
                request.totalMs()
        );
        return new RecordAiUsageEventResponse(true, usage.spaceId(), usage.feature());
    }
}
