package com.meetingmind.auth.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class KmsJwtAccessTokenIssuer implements AccessTokenIssuer {

    static final long ACCESS_TOKEN_SECONDS = 600L;
    static final List<String> AUDIENCES = List.of(
            "meetingmind-core",
            "meetingmind-ai",
            "meetingmind-livekit"
    );

    private final ObjectMapper objectMapper;
    private final AuthSigningProperties properties;
    private final SigningKeyRing keyRing;
    private final AsymmetricSigningKeyProvider keyProvider;

    KmsJwtAccessTokenIssuer(
            ObjectMapper objectMapper,
            AuthSigningProperties properties,
            SigningKeyRing keyRing,
            AsymmetricSigningKeyProvider keyProvider
    ) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.keyRing = keyRing;
        this.keyProvider = keyProvider;
    }

    @Override
    public List<IssuedAccessToken> issue(UUID userId, UUID authSessionId, Instant issuedAt) {
        SigningKeyRing.SigningKey key = keyRing.activeKey(issuedAt);
        RSAPublicKey publicKey = keyProvider.publicKey(key.kmsKeyId());
        return AUDIENCES.stream()
                .map(audience -> issueOne(userId, authSessionId, issuedAt, audience, key, publicKey))
                .toList();
    }

    private IssuedAccessToken issueOne(
            UUID userId,
            UUID authSessionId,
            Instant issuedAt,
            String audience,
            SigningKeyRing.SigningKey key,
            RSAPublicKey publicKey
    ) {
        long issuedAtSeconds = issuedAt.getEpochSecond();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("typ", "at+jwt");
        header.put("alg", "RS256");
        header.put("kid", key.kid());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", properties.issuer().toString());
        payload.put("aud", audience);
        payload.put("sub", userId.toString());
        payload.put("sid", authSessionId.toString());
        payload.put("jti", UUID.randomUUID().toString());
        payload.put("iat", issuedAtSeconds);
        payload.put("nbf", issuedAtSeconds);
        payload.put("exp", issuedAtSeconds + ACCESS_TOKEN_SECONDS);
        payload.put("ver", 1);

        String encodedHeader = encodeJson(header);
        String encodedPayload = encodeJson(payload);
        byte[] signingInput = (encodedHeader + "." + encodedPayload).getBytes(StandardCharsets.US_ASCII);
        byte[] signature = keyProvider.sign(key.kmsKeyId(), signingInput);
        verifyKmsSignature(publicKey, signingInput, signature);
        String token = encodedHeader + "." + encodedPayload + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        return new IssuedAccessToken(audience, token, ACCESS_TOKEN_SECONDS);
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    objectMapper.writeValueAsBytes(value)
            );
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private void verifyKmsSignature(RSAPublicKey publicKey, byte[] signingInput, byte[] signatureBytes) {
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(signingInput);
            if (!verifier.verify(signatureBytes)) {
                throw new IllegalArgumentException("KMS signature 검증에 실패했습니다.");
            }
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private AuthRuntimeException unavailable(Exception cause) {
        AuthRuntimeException exception = AuthRuntimeException.serviceUnavailable(
                "TOKEN_ISSUER_UNAVAILABLE",
                "access token 발급기를 사용할 수 없습니다."
        );
        exception.initCause(cause);
        return exception;
    }
}
