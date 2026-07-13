package com.meetingmind.demo.service;

import com.meetingmind.demo.dto.ai.AiChatResponse;
import com.meetingmind.demo.dto.ai.MeetingAiGatewayChatRequest;

public interface MeetingAiGatewayClient {

    AiChatResponse chat(MeetingAiGatewayChatRequest request);
}
