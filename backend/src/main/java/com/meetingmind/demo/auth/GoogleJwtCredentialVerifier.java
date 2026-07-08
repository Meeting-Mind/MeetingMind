package com.meetingmind.demo.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class GoogleJwtCredentialVerifier implements GoogleCredentialVerifier {

    private static final URI GOOGLE_CERTS_URI = URI.create("https://www.googleapis.com/oauth2/v3/certs");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AuthEnvironment environment;
    private final HttpClient httpClient;
    private final Clock clock;

    @Autowired
    public GoogleJwtCredentialVerifier(AuthEnvironment environment) {
        this(environment, HttpClient.newHttpClient(), Clock.systemUTC());
    }

    GoogleJwtCredentialVerifier(AuthEnvironment environment, HttpClient httpClient, Clock clock) {
        this.environment = environment;
        this.httpClient = httpClient;
        this.clock = clock;
    }

    @Override
    public GoogleUserInfo verify(String credential) {
        String[] parts = credential.split("\\.");
        if (parts.length != 3) {
            throw invalidCredentials();
        }

        Map<String, Object> header = decodeJson(parts[0]);
        Map<String, Object> payload = decodeJson(parts[1]);
        if (!"RS256".equals(header.get("alg"))) {
            throw invalidCredentials();
        }

        Object keyId = header.get("kid");
        if (!(keyId instanceof String kid) || kid.isBlank()) {
            throw invalidCredentials();
        }

        verifySignature(kid, parts[0] + "." + parts[1], parts[2]);
        validatePayload(payload);

        return new GoogleUserInfo(
                requiredString(payload, "sub"),
                requiredString(payload, "email"),
                optionalString(payload, "name", requiredString(payload, "email")),
                optionalString(payload, "picture", null)
        );
    }

    private void validatePayload(Map<String, Object> payload) {
        String clientId = environment.requireConfig("GOOGLE_CLIENT_ID", "VITE_GOOGLE_CLIENT_ID");
        if (!clientId.equals(payload.get("aud"))) {
            throw invalidCredentials();
        }

        long expiresAt = ((Number) payload.getOrDefault("exp", 0)).longValue();
        if (expiresAt <= Instant.now(clock).getEpochSecond()) {
            throw invalidCredentials();
        }

        Object emailVerified = payload.get("email_verified");
        if (!(emailVerified instanceof Boolean verified) || !verified) {
            throw invalidCredentials();
        }

        requiredString(payload, "sub");
        requiredString(payload, "email");
    }

    private void verifySignature(String kid, String signedContent, String signaturePart) {
        try {
            RSAPublicKey publicKey = fetchPublicKey(kid);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(signedContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (!signature.verify(Base64.getUrlDecoder().decode(signaturePart))) {
                throw invalidCredentials();
            }
        } catch (AuthException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidCredentials();
        }
    }

    private RSAPublicKey fetchPublicKey(String kid) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(GOOGLE_CERTS_URI).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw invalidCredentials();
        }

        Map<String, Object> document = OBJECT_MAPPER.readValue(response.body(), new TypeReference<>() {
        });
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keys = (List<Map<String, Object>>) document.getOrDefault("keys", List.of());
        for (Map<String, Object> key : keys) {
            if (!kid.equals(key.get("kid"))) {
                continue;
            }

            String modulus = requiredString(key, "n");
            String exponent = requiredString(key, "e");
            BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(modulus));
            BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(exponent));
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e));
        }

        throw invalidCredentials();
    }

    private static Map<String, Object> decodeJson(String tokenPart) {
        try {
            return OBJECT_MAPPER.readValue(Base64.getUrlDecoder().decode(tokenPart), new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw invalidCredentials();
        }
    }

    private static String requiredString(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw invalidCredentials();
        }
        return text;
    }

    private static String optionalString(Map<String, Object> values, String key, String fallback) {
        Object value = values.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return fallback;
    }

    private static AuthException invalidCredentials() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Google credential 검증에 실패했습니다.");
    }
}
