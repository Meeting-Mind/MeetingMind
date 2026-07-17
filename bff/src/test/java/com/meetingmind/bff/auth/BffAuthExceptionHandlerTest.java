package com.meetingmind.bff.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.meetingmind.bff.observability.BffRolloutMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class BffAuthExceptionHandlerTest {

    @Test
    void recordsOnlyTheExplicitSessionInvalidCode() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BffAuthExceptionHandler handler =
                new BffAuthExceptionHandler(new BffRolloutMetrics(meterRegistry));

        handler.handleAuthException(BffAuthException.of(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "invalid"));
        assertThat(meterRegistry.find("meetingmind.bff.session.invalid").counter()).isNull();

        handler.handleAuthException(BffAuthException.of(
                HttpStatus.UNAUTHORIZED,
                "SESSION_INVALID",
                "expired"));
        assertThat(meterRegistry.get("meetingmind.bff.session.invalid").counter().count())
                .isEqualTo(1.0);
    }
}
