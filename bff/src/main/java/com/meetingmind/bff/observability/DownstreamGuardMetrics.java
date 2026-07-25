package com.meetingmind.bff.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class DownstreamGuardMetrics {

    private final Counter rejectionCounter;
    private final Counter circuitOpenedCounter;
    private final AtomicInteger circuitOpenState;

    public DownstreamGuardMetrics(MeterRegistry meterRegistry, String service) {
        this.rejectionCounter = Counter.builder("meetingmind.bff.downstream.guard.rejections")
                .tags("service", service)
                .register(meterRegistry);
        this.circuitOpenedCounter = Counter.builder("meetingmind.bff.downstream.guard.opened")
                .tags("service", service)
                .register(meterRegistry);
        this.circuitOpenState = meterRegistry.gauge(
                "meetingmind.bff.downstream.guard.open",
                List.of(Tag.of("service", service)),
                new AtomicInteger(),
                AtomicInteger::get
        );
    }

    public void recordRejected() {
        rejectionCounter.increment();
    }

    public void recordCircuitOpened() {
        circuitOpenState.set(1);
        circuitOpenedCounter.increment();
    }

    public void recordCircuitClosed() {
        circuitOpenState.set(0);
    }
}
