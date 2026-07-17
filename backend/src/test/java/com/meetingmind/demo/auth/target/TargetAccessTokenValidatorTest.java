package com.meetingmind.demo.auth.target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TargetAccessTokenValidatorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-07-17T01:10:00Z");
    private static final String ISSUER = "https://auth.meetingmind.internal";
    private static final String AUDIENCE = "meetingmind-core";

    private KeyPair oldKey;
    private KeyPair newKey;

    @BeforeEach
    void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        oldKey = generator.generateKeyPair();
        newKey = generator.generateKeyPair();
    }

    @Test
    void validatesRequiredRs256ProfileAndReturnsIdentityOnly() throws Exception {
        SequenceJwksSource source = new SequenceJwksSource(List.of(jwks(Map.of("key-new", newKey))));
        TargetAccessTokenValidator validator = validator(source);
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        String token = token(
                newKey,
                "key-new",
                "RS256",
                ISSUER,
                AUDIENCE,
                userId,
                sessionId,
                tokenId,
                NOW.getEpochSecond(),
                NOW.getEpochSecond(),
                NOW.plusSeconds(600).getEpochSecond(),
                true
        );

        TargetAccessTokenValidator.Principal principal = validator.validate(token);

        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.authSessionId()).isEqualTo(sessionId);
        assertThat(principal.tokenId()).isEqualTo(tokenId);
        assertThat(principal.expiresAt()).isEqualTo(NOW.plusSeconds(600));
    }

    @Test
    void rejectsAlgorithmIssuerAudienceExpiryAndMissingRequiredClaim() throws Exception {
        SequenceJwksSource source = new SequenceJwksSource(List.of(jwks(Map.of("key-new", newKey))));
        TargetAccessTokenValidator validator = validator(source);
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();

        assertInvalid(validator, token(
                newKey, "key-new", "HS256", ISSUER, AUDIENCE, userId, sessionId, tokenId,
                NOW.getEpochSecond(), NOW.getEpochSecond(), NOW.plusSeconds(600).getEpochSecond(), true
        ));
        assertInvalid(validator, token(
                newKey, "key-new", "RS256", "https://attacker.invalid", AUDIENCE, userId, sessionId, tokenId,
                NOW.getEpochSecond(), NOW.getEpochSecond(), NOW.plusSeconds(600).getEpochSecond(), true
        ));
        assertInvalid(validator, token(
                newKey, "key-new", "RS256", ISSUER, "meetingmind-ai", userId, sessionId, tokenId,
                NOW.getEpochSecond(), NOW.getEpochSecond(), NOW.plusSeconds(600).getEpochSecond(), true
        ));
        assertInvalid(validator, token(
                newKey, "key-new", "RS256", ISSUER, AUDIENCE, userId, sessionId, tokenId,
                NOW.minusSeconds(700).getEpochSecond(),
                NOW.minusSeconds(700).getEpochSecond(),
                NOW.minusSeconds(100).getEpochSecond(),
                true
        ));
        assertInvalid(validator, token(
                newKey, "key-new", "RS256", ISSUER, AUDIENCE, userId, sessionId, tokenId,
                NOW.getEpochSecond(), NOW.getEpochSecond(), NOW.plusSeconds(600).getEpochSecond(), false
        ));
    }

    @Test
    void refreshesOnceForUnknownKidAndKeepsOldAndNewKeysDuringOverlap() throws Exception {
        SequenceJwksSource source = new SequenceJwksSource(List.of(
                jwks(Map.of("key-old", oldKey)),
                jwks(Map.of("key-old", oldKey, "key-new", newKey))
        ));
        TargetAccessTokenValidator validator = validator(source);
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String oldToken = token(
                oldKey, "key-old", "RS256", ISSUER, AUDIENCE, userId, sessionId, UUID.randomUUID(),
                NOW.getEpochSecond(), NOW.getEpochSecond(), NOW.plusSeconds(600).getEpochSecond(), true
        );
        String newToken = token(
                newKey, "key-new", "RS256", ISSUER, AUDIENCE, userId, sessionId, UUID.randomUUID(),
                NOW.getEpochSecond(), NOW.getEpochSecond(), NOW.plusSeconds(600).getEpochSecond(), true
        );

        assertThat(validator.validate(oldToken).userId()).isEqualTo(userId);
        assertThat(validator.validate(newToken).userId()).isEqualTo(userId);
        assertThat(validator.validate(oldToken).userId()).isEqualTo(userId);
        assertThat(source.fetchCount()).isEqualTo(2);
    }

    @Test
    void failsClosedWhenUnknownKidRemainsAfterOneRefresh() throws Exception {
        SequenceJwksSource source = new SequenceJwksSource(List.of(
                jwks(Map.of("key-old", oldKey)),
                jwks(Map.of("key-old", oldKey))
        ));
        TargetAccessTokenValidator validator = validator(source);
        String newToken = token(
                newKey, "key-new", "RS256", ISSUER, AUDIENCE,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                NOW.getEpochSecond(), NOW.getEpochSecond(), NOW.plusSeconds(600).getEpochSecond(), true
        );

        assertInvalid(validator, newToken);
        assertThat(source.fetchCount()).isEqualTo(2);
    }

    private TargetAccessTokenValidator validator(JwksSource source) {
        return new TargetAccessTokenValidator(
                ISSUER,
                AUDIENCE,
                source,
                OBJECT_MAPPER,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private void assertInvalid(TargetAccessTokenValidator validator, String token) {
        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(AccessTokenValidationException.class);
    }

    private String token(
            KeyPair pair,
            String kid,
            String algorithm,
            String issuer,
            String audience,
            UUID userId,
            UUID sessionId,
            UUID tokenId,
            long issuedAt,
            long notBefore,
            long expiresAt,
            boolean includeSessionId
    ) throws Exception {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("typ", "at+jwt");
        header.put("alg", algorithm);
        header.put("kid", kid);
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", issuer);
        claims.put("aud", audience);
        claims.put("sub", userId.toString());
        if (includeSessionId) {
            claims.put("sid", sessionId.toString());
        }
        claims.put("jti", tokenId.toString());
        claims.put("iat", issuedAt);
        claims.put("nbf", notBefore);
        claims.put("exp", expiresAt);
        claims.put("ver", 1);
        String encodedHeader = encode(OBJECT_MAPPER.writeValueAsBytes(header));
        String encodedClaims = encode(OBJECT_MAPPER.writeValueAsBytes(claims));
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(pair.getPrivate());
        signer.update((encodedHeader + "." + encodedClaims).getBytes(StandardCharsets.US_ASCII));
        return encodedHeader + "." + encodedClaims + "." + encode(signer.sign());
    }

    private String jwks(Map<String, KeyPair> keys) throws Exception {
        List<Map<String, Object>> values = new ArrayList<>();
        keys.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            RSAPublicKey key = (RSAPublicKey) entry.getValue().getPublic();
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("kty", "RSA");
            value.put("use", "sig");
            value.put("alg", "RS256");
            value.put("kid", entry.getKey());
            value.put("n", unsigned(key.getModulus()));
            value.put("e", unsigned(key.getPublicExponent()));
            values.add(value);
        });
        return OBJECT_MAPPER.writeValueAsString(Map.of("keys", values));
    }

    private String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return encode(bytes);
    }

    private String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static final class SequenceJwksSource implements JwksSource {
        private final List<String> documents;
        private int fetchCount;

        private SequenceJwksSource(List<String> documents) {
            this.documents = documents;
        }

        @Override
        public Response fetch(String etag) {
            int index = Math.min(fetchCount, documents.size() - 1);
            fetchCount++;
            return new Response(
                    false,
                    documents.get(index),
                    "\"etag-" + index + "\"",
                    Duration.ofMinutes(5)
            );
        }

        int fetchCount() {
            return fetchCount;
        }
    }
}
