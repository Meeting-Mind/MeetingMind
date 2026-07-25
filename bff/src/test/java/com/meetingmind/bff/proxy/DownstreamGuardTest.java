package com.meetingmind.bff.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class DownstreamGuardTest {

    @Test
    void rejectsBeyondTheBulkheadWithoutQueueing() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        DownstreamGuard guard = new DownstreamGuard(
                1, 3, Duration.ofSeconds(30), Clock.systemUTC(),
                new com.meetingmind.bff.observability.DownstreamGuardMetrics(meterRegistry, "test"));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> first = executor.submit(() -> guard.execute(() -> {
                entered.countDown();
                await(release);
                return "ok";
            }));
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> guard.execute(() -> "second"))
                    .isInstanceOf(DownstreamGuardRejectedException.class);
            assertThat(meterRegistry.get("meetingmind.bff.downstream.guard.rejections")
                    .tag("service", "test")
                    .counter()
                    .count()).isEqualTo(1.0d);

            release.countDown();
            assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo("ok");
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void opensAfterTheFailureThresholdAndClosesAfterOneSuccessfulProbe() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-16T00:00:00Z"));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        DownstreamGuard guard = new DownstreamGuard(
                1,
                2,
                Duration.ofSeconds(10),
                clock,
                new com.meetingmind.bff.observability.DownstreamGuardMetrics(meterRegistry, "test"));
        AtomicInteger calls = new AtomicInteger();

        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> guard.execute(() -> {
                        calls.incrementAndGet();
                        throw new DownstreamCallFailure();
                    }))
                    .isInstanceOf(DownstreamCallFailure.class);
        }
        assertThatThrownBy(() -> guard.execute(() -> {
                    calls.incrementAndGet();
                    return "blocked";
                }))
                .isInstanceOf(DownstreamGuardRejectedException.class);
        assertThat(calls).hasValue(2);

        clock.advance(Duration.ofSeconds(11));
        assertThat(guard.execute(() -> "probe-ok")).isEqualTo("probe-ok");
        assertThat(guard.execute(() -> "closed-ok")).isEqualTo("closed-ok");
        assertThat(meterRegistry.get("meetingmind.bff.downstream.guard.opened")
                .tag("service", "test")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("meetingmind.bff.downstream.guard.open")
                .tag("service", "test")
                .gauge()
                .value()).isEqualTo(0.0d);
    }

    @Test
    void aCallAdmittedBeforeOpeningCannotCloseTheCircuitLater() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        DownstreamGuard guard = new DownstreamGuard(
                2, 1, Duration.ofSeconds(30), Clock.systemUTC(),
                new com.meetingmind.bff.observability.DownstreamGuardMetrics(meterRegistry, "test"));
        CountDownLatch successEntered = new CountDownLatch(1);
        CountDownLatch releaseSuccess = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> earlierSuccess = executor.submit(() -> guard.execute(() -> {
                successEntered.countDown();
                await(releaseSuccess);
                return "late-success";
            }));
            assertThat(successEntered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> guard.execute(() -> {
                        throw new DownstreamCallFailure();
                    }))
                    .isInstanceOf(DownstreamCallFailure.class);
            releaseSuccess.countDown();
            assertThat(earlierSuccess.get(1, TimeUnit.SECONDS)).isEqualTo("late-success");

            assertThatThrownBy(() -> guard.execute(() -> "must-stay-open"))
                    .isInstanceOf(DownstreamGuardRejectedException.class);
            assertThat(meterRegistry.get("meetingmind.bff.downstream.guard.open")
                    .tag("service", "test")
                    .gauge()
                    .value()).isEqualTo(1.0d);
        } finally {
            releaseSuccess.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test latch timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test interrupted");
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
