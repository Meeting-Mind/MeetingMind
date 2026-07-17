package com.meetingmind.auth.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GoogleJwtCredentialVerifierTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-07-17T03:00:00Z");

    private HttpServer server;
    private AtomicReference<String> jwks;
    private AtomicInteger status;
    private AtomicInteger requests;

    @BeforeEach
    void startJwksServer() throws Exception {
        jwks = new AtomicReference<>("{}");
        status = new AtomicInteger(200);
        requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/keys", exchange -> {
            requests.incrementAndGet();
            byte[] body = jwks.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
            exchange.sendResponseHeaders(status.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopJwksServer() {
        server.stop(0);
    }

    @Test
    void validatesSignatureIssuerAudienceExpiryAndRefreshesUnknownKid() throws Exception {
        KeyPair first = rsaKeyPair();
        KeyPair second = rsaKeyPair();
        jwks.set(jwks("kid-1", (RSAPublicKey) first.getPublic()));
        GoogleJwtCredentialVerifier verifier = verifier();

        AuthModels.GoogleUser user = verifier.verify(jwt(
                first,
                "kid-1",
                "https://accounts.google.com",
                "meetingmind-test-client",
                NOW.plusSeconds(300),
                true
        ));
        assertThat(user.providerUserId()).isEqualTo("google-user-1");
        assertThat(user.email()).isEqualTo("user@meetingmind.test");
        assertThat(requests).hasValue(1);

        jwks.set(jwks("kid-2", (RSAPublicKey) second.getPublic()));
        verifier.verify(jwt(
                second,
                "kid-2",
                "accounts.google.com",
                "meetingmind-test-client",
                NOW.plusSeconds(300),
                true
        ));
        assertThat(requests).hasValue(2);

        assertInvalid(verifier, jwt(
                second,
                "kid-2",
                "https://attacker.invalid",
                "meetingmind-test-client",
                NOW.plusSeconds(300),
                true
        ));
        assertInvalid(verifier, jwt(
                second,
                "kid-2",
                "accounts.google.com",
                "wrong-client",
                NOW.plusSeconds(300),
                true
        ));
        assertInvalid(verifier, jwt(
                second,
                "kid-2",
                "accounts.google.com",
                "meetingmind-test-client",
                NOW.minusSeconds(1),
                true
        ));
        assertInvalid(verifier, jwt(
                second,
                "kid-2",
                "accounts.google.com",
                "meetingmind-test-client",
                NOW.plusSeconds(300),
                false
        ));
    }

    @Test
    void mapsJwksFailureWithoutProviderDetails() {
        status.set(503);
        assertThatThrownBy(() -> verifier().verify("header.payload.signature"))
                .isInstanceOfSatisfying(AuthRuntimeException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(401);
                    assertThat(exception.code()).isEqualTo("GOOGLE_CREDENTIAL_INVALID");
                });
    }

    @Test
    void mapsProviderFailureWhenWellFormedCredentialNeedsKeys() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        status.set(503);
        assertThatThrownBy(() -> verifier().verify(jwt(
                keyPair,
                "kid-unavailable",
                "accounts.google.com",
                "meetingmind-test-client",
                NOW.plusSeconds(300),
                true
        ))).isInstanceOfSatisfying(AuthRuntimeException.class, exception -> {
            assertThat(exception.status().value()).isEqualTo(503);
            assertThat(exception.code()).isEqualTo("AUTH_PROVIDER_UNAVAILABLE");
            assertThat(exception.getMessage()).doesNotContain("503", "localhost", "keys");
        });
    }

    private GoogleJwtCredentialVerifier verifier() {
        AuthRuntimeProperties.Google properties = new AuthRuntimeProperties.Google(
                List.of("meetingmind-test-client"),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/keys"),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofHours(1)
        );
        return new GoogleJwtCredentialVerifier(
                properties,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private void assertInvalid(GoogleJwtCredentialVerifier verifier, String credential) {
        assertThatThrownBy(() -> verifier.verify(credential))
                .isInstanceOfSatisfying(AuthRuntimeException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(401);
                    assertThat(exception.code()).isEqualTo("GOOGLE_CREDENTIAL_INVALID");
                });
    }

    private String jwt(
            KeyPair keyPair,
            String kid,
            String issuer,
            String audience,
            Instant expiresAt,
            boolean emailVerified
    ) throws Exception {
        String header = encodeJson(Map.of("alg", "RS256", "kid", kid, "typ", "JWT"));
        String payload = encodeJson(Map.of(
                "iss", issuer,
                "aud", audience,
                "sub", "google-user-1",
                "email", "User@MeetingMind.Test",
                "email_verified", emailVerified,
                "name", "Google User",
                "exp", expiresAt.getEpochSecond()
        ));
        String content = header + "." + payload;
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(content.getBytes(StandardCharsets.UTF_8));
        return content + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    private String jwks(String kid, RSAPublicKey key) throws Exception {
        return OBJECT_MAPPER.writeValueAsString(Map.of(
                "keys", List.of(Map.of(
                        "kty", "RSA",
                        "alg", "RS256",
                        "kid", kid,
                        "n", unsigned(key.getModulus()),
                        "e", unsigned(key.getPublicExponent())
                ))
        ));
    }

    private String encodeJson(Map<String, ?> value) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                OBJECT_MAPPER.writeValueAsBytes(value)
        );
    }

    private String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
