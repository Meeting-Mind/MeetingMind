package com.meetingmind.bff.auth;

import java.time.Duration;
import java.util.UUID;

public interface RefreshSingleFlightLock {

    boolean tryAcquire(UUID tokenBundleId, String owner, Duration lease);

    void release(UUID tokenBundleId, String owner);
}
