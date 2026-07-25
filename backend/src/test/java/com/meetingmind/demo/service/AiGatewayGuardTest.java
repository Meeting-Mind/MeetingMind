package com.meetingmind.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AiGatewayGuardTest {

    @Test
    void opensCircuitAfterFailureThreshold() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-25T00:00:00Z"));
        AiGatewayGuard guard = new AiGatewayGuard(new AiGatewayGuardPolicy(1, 2, Duration.ofSeconds(30)), clock);

        assertThatThrownBy(() -> guard.execute(() -> {
            throw new AiGatewayException("boom-1");
        })).isInstanceOf(AiGatewayException.class);

        assertThatThrownBy(() -> guard.execute(() -> {
            throw new AiGatewayException("boom-2");
        })).isInstanceOf(AiGatewayException.class);

        assertThatThrownBy(() -> guard.execute(() -> "never"))
                .isInstanceOf(AiGatewayGuardRejectedException.class);
    }

    @Test
    void allowsSingleHalfOpenProbeAfterOpenDuration() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-25T00:00:00Z"));
        AiGatewayGuard guard = new AiGatewayGuard(new AiGatewayGuardPolicy(2, 1, Duration.ofSeconds(10)), clock);

        assertThatThrownBy(() -> guard.execute(() -> {
            throw new AiGatewayException("boom");
        })).isInstanceOf(AiGatewayException.class);

        clock.advance(Duration.ofSeconds(11));
        AtomicInteger calls = new AtomicInteger();
        guard.execute(() -> {
            calls.incrementAndGet();
            return "ok";
        });
        assertThatCode(() -> guard.execute(() -> {
            calls.incrementAndGet();
            return "ok-2";
        })).doesNotThrowAnyException();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void rejectsConcurrentCallsBeyondBulkheadLimit() throws Exception {
        AiGatewayGuard guard = new AiGatewayGuard(
                new AiGatewayGuardPolicy(1, 3, Duration.ofSeconds(30)),
                Clock.systemUTC()
        );
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread first = new Thread(() -> guard.execute(() -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(exception);
            }
            return "ok";
        }));
        first.start();
        entered.await();

        try {
            assertThatThrownBy(() -> guard.execute(() -> "blocked"))
                    .isInstanceOf(AiGatewayGuardRejectedException.class);
        } finally {
            release.countDown();
            first.join();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
