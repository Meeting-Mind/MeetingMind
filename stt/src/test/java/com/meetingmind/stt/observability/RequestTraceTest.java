package com.meetingmind.stt.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RequestTraceTest {

    @AfterEach
    void clearTrace() {
        RequestTrace.clear();
    }

    @Test
    void preservesSafeIncomingTraceId() {
        assertThat(RequestTrace.normalize("trace-request-1234")).isEqualTo("trace-request-1234");
    }

    @Test
    void replacesUnsafeIncomingTraceId() {
        String traceId = RequestTrace.normalize("unsafe trace with spaces and secret");

        assertThat(traceId).matches("[a-f0-9]{32}");
        assertThat(traceId).doesNotContain("secret");
    }
}
