package com.meetingmind.demo.service;

import com.meetingmind.demo.dto.KnowledgeGraphResponse;
import com.meetingmind.demo.dto.ai.KnowledgeGraphGatewayRequest;

public interface KnowledgeGraphGatewayClient {

    KnowledgeGraphResponse graph(KnowledgeGraphGatewayRequest request);
}
