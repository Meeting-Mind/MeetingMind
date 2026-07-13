package com.meetingmind.demo.service;

import com.meetingmind.demo.dto.ai.ReportAiGatewayRequest;
import com.meetingmind.demo.dto.ai.ReportAiGatewayResponse;

public interface ReportAiGatewayClient {

    ReportAiGatewayResponse generate(ReportAiGatewayRequest request);
}
