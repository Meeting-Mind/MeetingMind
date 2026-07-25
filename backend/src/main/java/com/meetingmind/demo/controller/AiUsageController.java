package com.meetingmind.demo.controller;

import com.meetingmind.demo.auth.AuthService;
import com.meetingmind.demo.auth.AuthUserResponse;
import com.meetingmind.demo.domain.WorkspaceDomainService;
import com.meetingmind.demo.dto.RecordAiUsageEventRequest;
import com.meetingmind.demo.dto.RecordAiUsageEventResponse;
import com.meetingmind.demo.dto.SpaceAiUsageResponse;
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

    public AiUsageController(AuthService authService, WorkspaceDomainService workspaceDomainService) {
        this.authService = authService;
        this.workspaceDomainService = workspaceDomainService;
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
                null,
                usage.totalRequests(),
                usage.totalInputTokens(),
                usage.totalOutputTokens(),
                null,
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
