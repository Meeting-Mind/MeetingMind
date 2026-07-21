package com.meetingmind.demo.service;

import com.meetingmind.demo.dto.ai.AiChatResponse;
import com.meetingmind.demo.dto.ai.MeetingAiGatewayChatRequest;
import com.meetingmind.demo.dto.ai.MeetingAiGatewayTermRequest;
import com.meetingmind.demo.dto.ai.TermExplanationResponse;

public interface MeetingAiGatewayClient {

    AiChatResponse chat(MeetingAiGatewayChatRequest request);

    TermExplanationResponse explainTerm(MeetingAiGatewayTermRequest request);
}
