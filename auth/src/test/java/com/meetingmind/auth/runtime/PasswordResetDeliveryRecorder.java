package com.meetingmind.auth.runtime;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/** Test-only delivery adapter. It never appears in the runtime artifact. */
public final class PasswordResetDeliveryRecorder implements PasswordResetDelivery {

    private final AtomicReference<String> latestToken = new AtomicReference<>();

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void deliver(AuthModels.User user, String rawToken, Instant expiresAt) {
        latestToken.set(rawToken);
    }

    public String takeToken() {
        return latestToken.getAndSet(null);
    }
}
