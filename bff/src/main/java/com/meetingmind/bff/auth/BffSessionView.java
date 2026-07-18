package com.meetingmind.bff.auth;

import java.time.Instant;

public record BffSessionView(Instant expiresAt, Instant idleExpiresAt, boolean rememberMe) {}
