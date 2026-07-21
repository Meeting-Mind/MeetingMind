package com.meetingmind.auth.runtime;

import java.time.Instant;

/** Infrastructure adapter. Production supplies an email provider; the default fails closed. */
interface PasswordResetDelivery {

    boolean isAvailable();

    void deliver(AuthModels.User user, String rawToken, Instant expiresAt);
}
