package com.meetingmind.bff.tokenvault;

import java.time.Instant;

public record AudienceAccessToken(String token, Instant expiresAt) {

    public AudienceAccessToken {
        if (token == null || token.isBlank() || expiresAt == null) {
            throw TokenVaultException.of(TokenVaultException.Code.INVALID_BUNDLE);
        }
    }

    @Override
    public String toString() {
        return "AudienceAccessToken[REDACTED]";
    }
}
