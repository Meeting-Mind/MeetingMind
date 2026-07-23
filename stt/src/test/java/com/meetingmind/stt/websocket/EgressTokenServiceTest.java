package com.meetingmind.stt.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EgressTokenServiceTest {

    @BeforeAll
    static void setSecret() {
        System.setProperty("STT_EGRESS_WS_SECRET", "unit-test-secret");
    }

    @AfterAll
    static void clearSecret() {
        System.clearProperty("STT_EGRESS_WS_SECRET");
    }

    @Test
    void issuedTokenVerifiesForTheSameSessionBeforeExpiry() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-23T00:00:00Z"), ZoneOffset.UTC);
        EgressTokenService service = new EgressTokenService(clock);

        String token = service.issue("session-1");

        assertThat(service.verify("session-1", token)).isTrue();
    }

    @Test
    void tokenIsRejectedForADifferentSessionId() {
        EgressTokenService service = new EgressTokenService(Clock.systemUTC());

        String token = service.issue("session-1");

        assertThat(service.verify("session-2", token)).isFalse();
    }

    @Test
    void tokenIsRejectedOnceExpired() {
        Clock issuedAt = Clock.fixed(Instant.parse("2026-07-23T00:00:00Z"), ZoneOffset.UTC);
        EgressTokenService issuer = new EgressTokenService(issuedAt);
        String token = issuer.issue("session-1");

        Clock afterExpiry = Clock.fixed(Instant.parse("2026-07-23T00:10:00Z"), ZoneOffset.UTC);
        EgressTokenService verifier = new EgressTokenService(afterExpiry);

        assertThat(verifier.verify("session-1", token)).isFalse();
    }

    @Test
    void malformedTokenIsRejected() {
        EgressTokenService service = new EgressTokenService(Clock.systemUTC());

        assertThat(service.verify("session-1", "not-a-real-token")).isFalse();
        assertThat(service.verify("session-1", null)).isFalse();
    }
}
