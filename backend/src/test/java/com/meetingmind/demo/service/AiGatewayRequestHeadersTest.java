package com.meetingmind.demo.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpRequest;
import com.meetingmind.demo.observability.RequestTrace;
import org.junit.jupiter.api.Test;

class AiGatewayRequestHeadersTest {

    @Test
    void addsConfiguredServiceTokenWithoutExposingAnEmptyHeader() {
        RequestTrace.bind("trace-test-1234");
        try {
        HttpRequest authenticated = AiGatewayRequestHeaders.applyServiceToken(
                        HttpRequest.newBuilder(URI.create("http://localhost/internal")),
                        "service-secret"
                )
                .build();
        HttpRequest empty = AiGatewayRequestHeaders.applyServiceToken(
                        HttpRequest.newBuilder(URI.create("http://localhost/internal")),
                        ""
                )
                .build();

        assertThat(authenticated.headers().firstValue("X-MeetingMind-Service-Token"))
                .contains("service-secret");
        assertThat(empty.headers().firstValue("X-MeetingMind-Service-Token")).isEmpty();
        assertThat(authenticated.headers().firstValue(RequestTrace.HEADER_NAME)).contains("trace-test-1234");
        assertThat(empty.headers().firstValue(RequestTrace.HEADER_NAME)).contains("trace-test-1234");
        } finally {
            RequestTrace.clear();
        }
    }
}
