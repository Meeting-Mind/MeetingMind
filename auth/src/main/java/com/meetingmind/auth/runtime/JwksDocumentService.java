package com.meetingmind.auth.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JwksDocumentService {

    private final ObjectMapper objectMapper;
    private final SigningKeyRing keyRing;
    private final AsymmetricSigningKeyProvider keyProvider;
    private final Clock clock;

    JwksDocumentService(
            ObjectMapper objectMapper,
            SigningKeyRing keyRing,
            AsymmetricSigningKeyProvider keyProvider,
            Clock clock
    ) {
        this.objectMapper = objectMapper;
        this.keyRing = keyRing;
        this.keyProvider = keyProvider;
        this.clock = clock;
    }

    Document current() {
        try {
            Instant now = Instant.now(clock);
            List<Map<String, Object>> keys = keyRing.publishedKeys(now).stream()
                    .map(this::toJwk)
                    .toList();
            byte[] body = objectMapper.writeValueAsBytes(Map.of("keys", keys));
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body);
            String etag = "\"" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest) + "\"";
            return new Document(body, etag);
        } catch (AuthRuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            AuthRuntimeException unavailable = AuthRuntimeException.serviceUnavailable(
                    "JWKS_UNAVAILABLE",
                    "JWKS를 사용할 수 없습니다."
            );
            unavailable.initCause(exception);
            throw unavailable;
        }
    }

    private Map<String, Object> toJwk(SigningKeyRing.SigningKey signingKey) {
        RSAPublicKey publicKey = keyProvider.publicKey(signingKey.kmsKeyId());
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", signingKey.kid());
        jwk.put("n", unsignedBase64(publicKey.getModulus()));
        jwk.put("e", unsignedBase64(publicKey.getPublicExponent()));
        return jwk;
    }

    private String unsignedBase64(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    record Document(byte[] body, String etag) {
        Document {
            body = body.clone();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }
    }
}
