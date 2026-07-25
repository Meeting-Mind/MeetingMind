package com.meetingmind.demo.service;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

final class AiGatewayGuard {

    private final Semaphore bulkhead;
    private final int failureThreshold;
    private final java.time.Duration openDuration;
    private final Clock clock;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicReference<Instant> openUntil = new AtomicReference<>();
    private final AtomicBoolean halfOpenProbe = new AtomicBoolean();

    AiGatewayGuard(AiGatewayGuardPolicy policy, Clock clock) {
        this.bulkhead = new Semaphore(policy.maxConcurrent());
        this.failureThreshold = policy.failureThreshold();
        this.openDuration = policy.openDuration();
        this.clock = clock;
    }

    <T> T execute(Supplier<T> operation) {
        boolean probe = acquireCircuitPermission();
        if (!bulkhead.tryAcquire()) {
            if (probe) {
                halfOpenProbe.set(false);
            }
            throw new AiGatewayGuardRejectedException();
        }
        try {
            T result = operation.get();
            recordSuccess(probe);
            return result;
        } catch (AiGatewayException exception) {
            recordFailure(probe);
            throw exception;
        } catch (RuntimeException exception) {
            if (probe) {
                halfOpenProbe.set(false);
            }
            throw exception;
        } finally {
            bulkhead.release();
        }
    }

    private boolean acquireCircuitPermission() {
        Instant until = openUntil.get();
        if (until == null) {
            return false;
        }
        if (clock.instant().isBefore(until)) {
            throw new AiGatewayGuardRejectedException();
        }
        if (!halfOpenProbe.compareAndSet(false, true)) {
            throw new AiGatewayGuardRejectedException();
        }
        return true;
    }

    private void recordSuccess(boolean probe) {
        if (probe) {
            consecutiveFailures.set(0);
            openUntil.set(null);
            halfOpenProbe.set(false);
            return;
        }
        if (openUntil.get() == null) {
            consecutiveFailures.set(0);
        }
    }

    private void recordFailure(boolean probe) {
        int failures = consecutiveFailures.incrementAndGet();
        if (probe || failures >= failureThreshold) {
            openUntil.set(clock.instant().plus(openDuration));
            consecutiveFailures.set(0);
        }
        halfOpenProbe.set(false);
    }
}
