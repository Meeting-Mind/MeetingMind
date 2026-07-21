package com.meetingmind.demo.controller;

import com.meetingmind.demo.dto.ai.AiChatResponse;
import com.meetingmind.demo.dto.ai.BackendMeetingAiChatRequest;
import com.meetingmind.demo.dto.ai.ExplainMeetingTermRequest;
import com.meetingmind.demo.dto.ai.TermExplanationResponse;
import com.meetingmind.demo.service.MeetingAiService;
import com.meetingmind.demo.service.MeetingTermExplanationService;
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
    private final MeetingTermExplanationService meetingTermExplanationService;

    public MeetingAiController(
            MeetingAiService meetingAiService,
            MeetingTermExplanationService meetingTermExplanationService
    ) {
        this.meetingAiService = meetingAiService;
        this.meetingTermExplanationService = meetingTermExplanationService;
    }

    @PostMapping("/{meetingId}/ai/chat")
    public AiChatResponse chat(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId,
            @Valid @RequestBody BackendMeetingAiChatRequest request
    ) {
        return meetingAiService.chat(authorizationHeader, meetingId, request);
    }

    @PostMapping("/{meetingId}/terms/explain")
    public TermExplanationResponse explainTerm(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable String meetingId,
            @Valid @RequestBody ExplainMeetingTermRequest request
    ) {
        return meetingTermExplanationService.explain(authorizationHeader, meetingId, request.term());
    }
}
