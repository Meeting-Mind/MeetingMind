package com.meetingmind.demo.service;

import com.meetingmind.demo.observability.RequestTrace;
import java.net.http.HttpRequest;

final class AiGatewayRequestHeaders {

    private static final String SERVICE_TOKEN_HEADER = "X-MeetingMind-Service-Token";

    private AiGatewayRequestHeaders() {
    }

    static HttpRequest.Builder applyServiceToken(HttpRequest.Builder builder, String serviceToken) {
        if (serviceToken != null && !serviceToken.isBlank()) {
            builder.header(SERVICE_TOKEN_HEADER, serviceToken);
        }
        builder.header(RequestTrace.HEADER_NAME, RequestTrace.currentOrCreate());
        return builder;
    }
}
