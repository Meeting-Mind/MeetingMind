package com.meetingmind.auth.runtime;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AccessTokenIssuer {

    List<IssuedAccessToken> issue(UUID userId, UUID authSessionId, Instant issuedAt);

    record IssuedAccessToken(String audience, String token, long expiresIn) {
    }
}
