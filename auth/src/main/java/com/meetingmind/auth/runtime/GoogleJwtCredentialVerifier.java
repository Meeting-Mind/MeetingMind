package com.meetingmind.auth.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class GoogleJwtCredentialVerifier implements GoogleCredentialVerifier {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
    };
    private static final List<String> ALLOWED_ISSUERS = List.of(
            "accounts.google.com",
            "https://accounts.google.com"
    );

    private final AuthRuntimeProperties.Google properties;
    private final HttpClient httpClient;
    private final Clock clock;
    private volatile CachedKeys cachedKeys = new CachedKeys(Map.of(), Instant.EPOCH);

    @Autowired
    GoogleJwtCredentialVerifier(AuthRuntimeProperties properties, Clock clock) {
        this(
                properties.google(),
                HttpClient.newBuilder().connectTimeout(properties.google().connectTimeout()).build(),
                clock
        );
    }

    GoogleJwtCredentialVerifier(
            AuthRuntimeProperties.Google properties,
            HttpClient httpClient,
            Clock clock
    ) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.clock = clock;
    }

    @Override
    public AuthModels.GoogleUser verify(String credential) {
        if (properties.clientIds().isEmpty()) {
            throw AuthRuntimeException.serviceUnavailable(
                    "AUTH_PROVIDER_UNAVAILABLE",
                    "Google 로그인 설정을 사용할 수 없습니다."
            );
        }
        String[] parts = credential.split("\\.", -1);
        if (parts.length != 3) {
            throw invalidCredential();
        }

        Map<String, Object> header = decodeJson(parts[0]);
        Map<String, Object> payload = decodeJson(parts[1]);
        if (!"RS256".equals(header.get("alg"))) {
            throw invalidCredential();
        }
        String keyId = requiredString(header, "kid");
        verifySignature(keyId, parts[0] + "." + parts[1], parts[2]);
        validatePayload(payload);

        return new AuthModels.GoogleUser(
                requiredString(payload, "sub"),
                canonicalEmail(requiredString(payload, "email")),
                optionalString(payload, "name", requiredString(payload, "email")),
                optionalString(payload, "picture", null)
        );
    }

    private void validatePayload(Map<String, Object> payload) {
        if (!ALLOWED_ISSUERS.contains(payload.get("iss"))) {
            throw invalidCredential();
        }
        Object audience = payload.get("aud");
        if (!(audience instanceof String value) || !properties.clientIds().contains(value)) {
            throw invalidCredential();
        }
        Object expiresAt = payload.get("exp");
        if (!(expiresAt instanceof Number number)
                || number.longValue() <= Instant.now(clock).getEpochSecond()) {
            throw invalidCredential();
        }
        if (!Boolean.TRUE.equals(payload.get("email_verified"))) {
            throw invalidCredential();
        }
        requiredString(payload, "sub");
        requiredString(payload, "email");
    }

    private void verifySignature(String keyId, String content, String signaturePart) {
        try {
            RSAPublicKey publicKey = resolveKey(keyId);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (!signature.verify(Base64.getUrlDecoder().decode(signaturePart))) {
                throw invalidCredential();
            }
        } catch (AuthRuntimeException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw invalidCredential();
        } catch (Exception exception) {
            throw AuthRuntimeException.serviceUnavailable(
                    "AUTH_PROVIDER_UNAVAILABLE",
                    "Google 공개키 검증을 사용할 수 없습니다."
            );
        }
    }

    private RSAPublicKey resolveKey(String keyId) {
        Instant now = Instant.now(clock);
        CachedKeys current = cachedKeys;
        if (current.expiresAt().isAfter(now) && current.keys().containsKey(keyId)) {
            return current.keys().get(keyId);
        }
        CachedKeys refreshed = refreshKeys(now, current.expiresAt().isAfter(now));
        RSAPublicKey key = refreshed.keys().get(keyId);
        if (key == null) {
            throw invalidCredential();
        }
        return key;
    }

    private synchronized CachedKeys refreshKeys(Instant now, boolean force) {
        CachedKeys current = cachedKeys;
        if (!force && current.expiresAt().isAfter(now) && !current.keys().isEmpty()) {
            return current;
        }
        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(properties.jwksUri())
                            .timeout(properties.requestTimeout())
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw providerUnavailable();
            }
            Map<String, Object> document = OBJECT_MAPPER.readValue(response.body(), JSON_OBJECT);
            Object rawKeys = document.get("keys");
            if (!(rawKeys instanceof List<?> list)) {
                throw providerUnavailable();
            }
            Map<String, RSAPublicKey> keys = new HashMap<>();
            for (Object value : list) {
                if (value instanceof Map<?, ?> rawKey) {
                    addKey(keys, rawKey);
                }
            }
            if (keys.isEmpty()) {
                throw providerUnavailable();
            }
            Duration ttl = cacheTtl(response);
            cachedKeys = new CachedKeys(Map.copyOf(keys), now.plus(ttl));
            return cachedKeys;
        } catch (AuthRuntimeException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw providerUnavailable();
        } catch (Exception exception) {
            throw providerUnavailable();
        }
    }

    private void addKey(Map<String, RSAPublicKey> keys, Map<?, ?> rawKey) throws Exception {
        if (!"RSA".equals(rawKey.get("kty")) || !"RS256".equals(rawKey.get("alg"))) {
            return;
        }
        Object rawKid = rawKey.get("kid");
        Object rawModulus = rawKey.get("n");
        Object rawExponent = rawKey.get("e");
        if (!(rawKid instanceof String kid)
                || !(rawModulus instanceof String modulus)
                || !(rawExponent instanceof String exponent)
                || kid.isBlank()) {
            return;
        }
        BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(modulus));
        BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(exponent));
        keys.put(
                kid,
                (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e))
        );
    }

    private Duration cacheTtl(HttpResponse<?> response) {
        long maxAgeSeconds = response.headers().firstValue("Cache-Control")
                .map(GoogleJwtCredentialVerifier::parseMaxAge)
                .orElse(300L);
        Duration providerTtl = Duration.ofSeconds(Math.max(1, maxAgeSeconds));
        return providerTtl.compareTo(properties.maximumCacheTtl()) > 0
                ? properties.maximumCacheTtl()
                : providerTtl;
    }

    private static long parseMaxAge(String cacheControl) {
        for (String directive : cacheControl.split(",")) {
            String value = directive.trim().toLowerCase(Locale.ROOT);
            if (value.startsWith("max-age=")) {
                try {
                    return Long.parseLong(value.substring("max-age=".length()));
                } catch (NumberFormatException ignored) {
                    return 300L;
                }
            }
        }
        return 300L;
    }

    private static Map<String, Object> decodeJson(String part) {
        try {
            return OBJECT_MAPPER.readValue(Base64.getUrlDecoder().decode(part), JSON_OBJECT);
        } catch (Exception exception) {
            throw invalidCredential();
        }
    }

    private static String requiredString(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw invalidCredential();
        }
        return text;
    }

    private static String optionalString(Map<String, Object> values, String key, String fallback) {
        Object value = values.get(key);
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private static String canonicalEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static AuthRuntimeException invalidCredential() {
        return AuthRuntimeException.unauthorized(
                "GOOGLE_CREDENTIAL_INVALID",
                "Google credential이 올바르지 않습니다."
        );
    }

    private static AuthRuntimeException providerUnavailable() {
        return AuthRuntimeException.serviceUnavailable(
                "AUTH_PROVIDER_UNAVAILABLE",
                "Google 인증 제공자를 사용할 수 없습니다."
        );
    }

    private record CachedKeys(Map<String, RSAPublicKey> keys, Instant expiresAt) {
    }
}
