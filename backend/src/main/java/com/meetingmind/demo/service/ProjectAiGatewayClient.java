package com.meetingmind.demo.service;

import com.meetingmind.demo.dto.ai.AiChatResponse;
import com.meetingmind.demo.dto.ai.ProjectAiGatewayChatRequest;

public interface ProjectAiGatewayClient {

    AiChatResponse chat(ProjectAiGatewayChatRequest request);
}
