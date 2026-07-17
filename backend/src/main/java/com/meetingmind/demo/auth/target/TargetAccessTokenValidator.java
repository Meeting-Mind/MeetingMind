package com.meetingmind.demo.auth.target;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TargetAccessTokenValidator {

    static final Duration MAXIMUM_JWKS_CACHE = Duration.ofMinutes(5);
    static final Duration CLOCK_SKEW = Duration.ofSeconds(60);
    static final long ACCESS_TOKEN_SECONDS = 600L;
    private static final int MAX_TOKEN_LENGTH = 16_384;

    private final String issuer;
    private final String audience;
    private final JwksSource jwksSource;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private volatile CachedJwks cache;

    public TargetAccessTokenValidator(
            String issuer,
            String audience,
            JwksSource jwksSource,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("access issuer가 필요합니다.");
        }
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("access audience가 필요합니다.");
        }
        this.issuer = issuer;
        this.audience = audience;
        this.jwksSource = java.util.Objects.requireNonNull(jwksSource);
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper);
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    public Principal validate(String token) {
        try {
            String[] parts = splitToken(token);
            JsonNode header = decodeObject(parts[0], "header");
            requireText(header, "alg", "RS256");
            requireText(header, "typ", "at+jwt");
            String kid = requireNonBlankText(header, "kid");

            Instant now = Instant.now(clock);
            CachedJwks current = currentKeys(now);
            RSAPublicKey publicKey = current.keys().get(kid);
            if (publicKey == null) {
                current = refreshKeys(now, true);
                publicKey = current.keys().get(kid);
            }
            if (publicKey == null) {
                throw invalid("알 수 없는 access token kid입니다.");
            }
            verifySignature(publicKey, parts);

            JsonNode claims = decodeObject(parts[1], "payload");
            requireText(claims, "iss", issuer);
            requireText(claims, "aud", audience);
            UUID subject = requireUuid(claims, "sub");
            UUID sessionId = requireUuid(claims, "sid");
            UUID tokenId = requireUuid(claims, "jti");
            long issuedAt = requireLong(claims, "iat");
            long notBefore = requireLong(claims, "nbf");
            long expiresAt = requireLong(claims, "exp");
            long version = requireLong(claims, "ver");
            if (version != 1L) {
                throw invalid("access token profile version이 올바르지 않습니다.");
            }
            if (notBefore != issuedAt || tokenLifetime(expiresAt, issuedAt) != ACCESS_TOKEN_SECONDS) {
                throw invalid("access token 시간 profile이 올바르지 않습니다.");
            }
            validateTime(now.getEpochSecond(), issuedAt, notBefore, expiresAt);
            return new Principal(
                    subject,
                    sessionId,
                    tokenId,
                    Instant.ofEpochSecond(issuedAt),
                    Instant.ofEpochSecond(expiresAt)
            );
        } catch (AccessTokenValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AccessTokenValidationException("access token을 검증할 수 없습니다.", exception);
        }
    }

    private CachedJwks currentKeys(Instant now) {
        CachedJwks current = cache;
        if (current == null || !now.isBefore(current.expiresAt())) {
            return refreshKeys(now, false);
        }
        return current;
    }

    private synchronized CachedJwks refreshKeys(Instant now, boolean force) {
        CachedJwks current = cache;
        if (!force && current != null && now.isBefore(current.expiresAt())) {
            return current;
        }
        try {
            JwksSource.Response response = jwksSource.fetch(current == null ? null : current.etag());
            Duration ttl = response.maxAge().compareTo(MAXIMUM_JWKS_CACHE) > 0
                    ? MAXIMUM_JWKS_CACHE
                    : response.maxAge();
            if (response.notModified()) {
                if (current == null) {
                    throw invalid("초기 JWKS 응답이 비어 있습니다.");
                }
                cache = new CachedJwks(current.keys(), response.etag(), now.plus(ttl));
            } else {
                cache = new CachedJwks(parseJwks(response.body()), response.etag(), now.plus(ttl));
            }
            return cache;
        } catch (AccessTokenValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AccessTokenValidationException("JWKS를 갱신할 수 없습니다.", exception);
        }
    }

    private Map<String, RSAPublicKey> parseJwks(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode keysNode = root.path("keys");
            if (!keysNode.isArray() || keysNode.isEmpty()) {
                throw invalid("JWKS key가 없습니다.");
            }
            Map<String, RSAPublicKey> keys = new HashMap<>();
            Set<String> seenKids = new HashSet<>();
            for (JsonNode key : keysNode) {
                requireText(key, "kty", "RSA");
                requireText(key, "use", "sig");
                requireText(key, "alg", "RS256");
                String kid = requireNonBlankText(key, "kid");
                if (!seenKids.add(kid)) {
                    throw invalid("JWKS kid가 중복됩니다.");
                }
                BigInteger modulus = unsignedInteger(requireNonBlankText(key, "n"));
                BigInteger exponent = unsignedInteger(requireNonBlankText(key, "e"));
                var generated = KeyFactory.getInstance("RSA").generatePublic(
                        new RSAPublicKeySpec(modulus, exponent)
                );
                if (!(generated instanceof RSAPublicKey rsaPublicKey)
                        || rsaPublicKey.getModulus().bitLength() != 2048) {
                    throw invalid("JWKS key가 RSA-2048이 아닙니다.");
                }
                keys.put(kid, rsaPublicKey);
            }
            return Map.copyOf(keys);
        } catch (AccessTokenValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AccessTokenValidationException("JWKS 형식이 올바르지 않습니다.", exception);
        }
    }

    private void verifySignature(RSAPublicKey publicKey, String[] parts) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);
            if (!signature.verify(signatureBytes)) {
                throw invalid("access token 서명이 올바르지 않습니다.");
            }
        } catch (AccessTokenValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AccessTokenValidationException("access token 서명을 검증할 수 없습니다.", exception);
        }
    }

    private void validateTime(long now, long issuedAt, long notBefore, long expiresAt) {
        long skew = CLOCK_SKEW.toSeconds();
        if (issuedAt > now + skew || notBefore > now + skew) {
            throw invalid("access token이 아직 유효하지 않습니다.");
        }
        if (expiresAt <= now - skew) {
            throw invalid("access token이 만료됐습니다.");
        }
    }

    private long tokenLifetime(long expiresAt, long issuedAt) {
        try {
            return Math.subtractExact(expiresAt, issuedAt);
        } catch (ArithmeticException exception) {
            throw invalid("access token 시간 profile이 올바르지 않습니다.");
        }
    }

    private String[] splitToken(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw invalid("access token 형식이 올바르지 않습니다.");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw invalid("access token 형식이 올바르지 않습니다.");
        }
        return parts;
    }

    private JsonNode decodeObject(String encoded, String part) {
        try {
            JsonNode node = objectMapper.readTree(Base64.getUrlDecoder().decode(encoded));
            if (!node.isObject()) {
                throw invalid("access token " + part + "가 객체가 아닙니다.");
            }
            return node;
        } catch (AccessTokenValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AccessTokenValidationException("access token " + part + "를 읽을 수 없습니다.", exception);
        }
    }

    private void requireText(JsonNode node, String field, String expected) {
        if (!expected.equals(requireNonBlankText(node, field))) {
            throw invalid("access token " + field + "가 올바르지 않습니다.");
        }
    }

    private String requireNonBlankText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw invalid("access token " + field + "가 필요합니다.");
        }
        return value.textValue();
    }

    private long requireLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw invalid("access token " + field + "가 필요합니다.");
        }
        return value.longValue();
    }

    private UUID requireUuid(JsonNode node, String field) {
        try {
            return UUID.fromString(requireNonBlankText(node, field));
        } catch (IllegalArgumentException exception) {
            throw invalid("access token " + field + "가 UUID가 아닙니다.");
        }
    }

    private BigInteger unsignedInteger(String encoded) {
        byte[] bytes = Base64.getUrlDecoder().decode(encoded);
        if (bytes.length == 0) {
            throw invalid("JWKS RSA 값이 비어 있습니다.");
        }
        return new BigInteger(1, bytes);
    }

    private AccessTokenValidationException invalid(String message) {
        return new AccessTokenValidationException(message);
    }

    public record Principal(
            UUID userId,
            UUID authSessionId,
            UUID tokenId,
            Instant issuedAt,
            Instant expiresAt
    ) {
    }

    private record CachedJwks(Map<String, RSAPublicKey> keys, String etag, Instant expiresAt) {
    }
}
