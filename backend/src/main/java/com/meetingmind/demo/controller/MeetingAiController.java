package com.meetingmind.demo.controller;

import com.meetingmind.demo.dto.ai.AiChatResponse;
import com.meetingmind.demo.dto.ai.BackendMeetingAiChatRequest;
import com.meetingmind.demo.service.MeetingAiService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meetings")
public class MeetingAiController {

    private final MeetingAiService meetingAiService;

    public MeetingAiController(MeetingAiService meetingAiService) {
        this.meetingAiService = meetingAiService;
    }

    @PostMapping("/{meetingId}/ai/chat")
    public AiChatResponse chat(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId,
            @Valid @RequestBody BackendMeetingAiChatRequest request
    ) {
        return meetingAiService.chat(authorizationHeader, meetingId, request);
    }
}
