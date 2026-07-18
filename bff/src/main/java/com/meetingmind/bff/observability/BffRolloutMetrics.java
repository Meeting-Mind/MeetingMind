package com.meetingmind.bff.observability;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class BffRolloutMetrics {

    static final String BROWSER_REQUESTS = "meetingmind.bff.browser.requests";
    static final String REFRESH = "meetingmind.bff.refresh";
    static final String SESSION_INVALID = "meetingmind.bff.session.invalid";

    private final MeterRegistry meterRegistry;

    public BffRolloutMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordBrowserRequest(String operation, String outcome, long durationNanos) {
        meterRegistry.timer(BROWSER_REQUESTS, "operation", operation, "outcome", outcome)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordRefresh(String outcome) {
        meterRegistry.counter(REFRESH, "outcome", outcome).increment();
    }

    public void recordSessionInvalid() {
        meterRegistry.counter(SESSION_INVALID).increment();
    }
}
