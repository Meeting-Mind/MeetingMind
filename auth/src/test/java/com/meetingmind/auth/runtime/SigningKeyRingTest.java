package com.meetingmind.auth.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SigningKeyRingTest {

    private static final Instant NOW = Instant.parse("2026-07-17T01:10:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void acceptsPrepublishedActiveKeyAndOneHourPreviousOverlap() {
        SigningKeyRing ring = parse("""
                {
                  "activeKid":"key-new",
                  "activeSince":"2026-07-17T01:05:00Z",
                  "rotationMode":"REGULAR",
                  "keys":[
                    {
                      "kid":"key-old",
                      "kmsKeyId":"kms-old",
                      "publishedAt":"2026-04-01T00:00:00Z",
                      "publishUntil":"2026-07-17T02:05:00Z"
                    },
                    {
                      "kid":"key-new",
                      "kmsKeyId":"kms-new",
                      "publishedAt":"2026-07-17T01:00:00Z"
                    }
                  ]
                }
                """);

        assertThat(ring.activeKey(NOW).kid()).isEqualTo("key-new");
        assertThat(ring.publishedKeys(NOW))
                .extracting(SigningKeyRing.SigningKey::kid)
                .containsExactly("key-new", "key-old");
    }

    @Test
    void rejectsRegularRotationWithoutPrepublishOrPreviousOverlap() {
        assertThatThrownBy(() -> parse("""
                {
                  "activeKid":"key-new",
                  "activeSince":"2026-07-17T01:05:00Z",
                  "keys":[
                    {"kid":"key-old","kmsKeyId":"kms-old","publishedAt":"2026-04-01T00:00:00Z","publishUntil":"2026-07-17T01:30:00Z"},
                    {"kid":"key-new","kmsKeyId":"kms-new","publishedAt":"2026-07-17T01:02:00Z"}
                  ]
                }
                """))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsExplicitEmergencyRemovalWithoutPreviousKey() {
        SigningKeyRing ring = parse("""
                {
                  "activeKid":"key-emergency",
                  "activeSince":"2026-07-17T01:05:00Z",
                  "rotationMode":"EMERGENCY",
                  "keys":[
                    {"kid":"key-emergency","kmsKeyId":"kms-emergency","publishedAt":"2026-07-17T01:00:00Z"}
                  ]
                }
                """);

        assertThat(ring.publishedKeys(NOW))
                .extracting(SigningKeyRing.SigningKey::kid)
                .containsExactly("key-emergency");
    }

    private SigningKeyRing parse(String json) {
        return SigningKeyRing.parse(new ObjectMapper(), json, CLOCK);
    }
}
