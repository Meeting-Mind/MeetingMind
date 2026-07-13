package com.meetingmind.demo.controller;

import com.meetingmind.demo.dto.ai.AiChatResponse;
import com.meetingmind.demo.dto.ai.BackendProjectAiChatRequest;
import com.meetingmind.demo.service.ProjectAiService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces")
public class ProjectAiController {

    private final ProjectAiService projectAiService;

    public ProjectAiController(ProjectAiService projectAiService) {
        this.projectAiService = projectAiService;
    }

    @PostMapping("/{spaceId}/ai/chat")
    public AiChatResponse chat(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String spaceId,
            @Valid @RequestBody BackendProjectAiChatRequest request
    ) {
        return projectAiService.chat(authorizationHeader, spaceId, request);
    }
}
