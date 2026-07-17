package com.meetingmind.auth.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JwksDocumentServiceTest {

    @Test
    void publishesActiveAndOverlapKeysWithStableEtag() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        Instant now = Instant.parse("2026-07-17T01:10:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        SigningKeyRing ring = SigningKeyRing.parse(objectMapper, """
                {
                  "activeKid":"key-new",
                  "activeSince":"2026-07-17T01:05:00Z",
                  "keys":[
                    {"kid":"key-old","kmsKeyId":"kms-old","publishedAt":"2026-04-01T00:00:00Z","publishUntil":"2026-07-17T02:05:00Z"},
                    {"kid":"key-new","kmsKeyId":"kms-new","publishedAt":"2026-07-17T01:00:00Z"}
                  ]
                }
                """, clock);
        KeyPair oldKey = TestSigningKeyProvider.generateKeyPair();
        KeyPair newKey = TestSigningKeyProvider.generateKeyPair();
        JwksDocumentService service = new JwksDocumentService(
                objectMapper,
                ring,
                new TestSigningKeyProvider(Map.of("kms-old", oldKey, "kms-new", newKey)),
                clock
        );

        JwksDocumentService.Document first = service.current();
        JwksDocumentService.Document second = service.current();
        JsonNode body = objectMapper.readTree(first.body());

        assertThat(body.path("keys")).hasSize(2);
        assertThat(body.path("keys").findValuesAsText("kid")).containsExactly("key-new", "key-old");
        assertThat(body.path("keys").findValuesAsText("alg")).containsOnly("RS256");
        assertThat(first.etag()).isEqualTo(second.etag()).startsWith("\"").endsWith("\"");
    }
}
