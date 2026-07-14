package com.meetingmind.demo.service;

import com.meetingmind.demo.dto.ai.TaskAiGatewayRequest;
import com.meetingmind.demo.dto.ai.TaskAiGatewayResponse;

public interface TaskAiGatewayClient {
    TaskAiGatewayResponse extract(TaskAiGatewayRequest request);
}
