package com.meetingmind.auth.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KmsJwtAccessTokenIssuerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-07-17T01:10:00Z");

    @Test
    void issuesThreeAudienceBoundRs256TokensWithRequiredClaims() throws Exception {
        KeyPair keyPair = TestSigningKeyProvider.generateKeyPair();
        SigningKeyRing ring = SigningKeyRing.parse(OBJECT_MAPPER, """
                {
                  "activeKid":"key-new",
                  "activeSince":"2026-07-17T01:05:00Z",
                  "keys":[
                    {"kid":"key-new","kmsKeyId":"kms-new","publishedAt":"2026-07-17T01:00:00Z"}
                  ]
                }
                """, Clock.fixed(NOW, ZoneOffset.UTC));
        var issuer = new KmsJwtAccessTokenIssuer(
                OBJECT_MAPPER,
                new AuthSigningProperties(
                        "aws-kms",
                        URI.create("https://auth.meetingmind.internal"),
                        "{}"
                ),
                ring,
                new TestSigningKeyProvider(Map.of("kms-new", keyPair))
        );
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        var tokens = issuer.issue(userId, sessionId, NOW);

        assertThat(tokens).extracting(AccessTokenIssuer.IssuedAccessToken::audience)
                .containsExactlyElementsOf(KmsJwtAccessTokenIssuer.AUDIENCES);
        Set<String> tokenIds = new HashSet<>();
        for (AccessTokenIssuer.IssuedAccessToken issued : tokens) {
            assertThat(issued.expiresIn()).isEqualTo(600);
            String[] parts = issued.token().split("\\.");
            JsonNode header = decode(parts[0]);
            JsonNode claims = decode(parts[1]);
            assertThat(header.path("typ").asText()).isEqualTo("at+jwt");
            assertThat(header.path("alg").asText()).isEqualTo("RS256");
            assertThat(header.path("kid").asText()).isEqualTo("key-new");
            assertThat(claims.path("iss").asText()).isEqualTo("https://auth.meetingmind.internal");
            assertThat(claims.path("aud").asText()).isEqualTo(issued.audience());
            assertThat(claims.path("sub").asText()).isEqualTo(userId.toString());
            assertThat(claims.path("sid").asText()).isEqualTo(sessionId.toString());
            assertThat(claims.path("iat").asLong()).isEqualTo(NOW.getEpochSecond());
            assertThat(claims.path("nbf").asLong()).isEqualTo(NOW.getEpochSecond());
            assertThat(claims.path("exp").asLong() - claims.path("iat").asLong()).isEqualTo(600);
            assertThat(claims.path("ver").asInt()).isEqualTo(1);
            assertThat(tokenIds.add(claims.path("jti").asText())).isTrue();
            assertSignature(keyPair, parts);
        }
    }

    private JsonNode decode(String value) throws Exception {
        return OBJECT_MAPPER.readTree(Base64.getUrlDecoder().decode(value));
    }

    private void assertSignature(KeyPair keyPair, String[] parts) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(keyPair.getPublic());
        signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(signature.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }
}
