package com.meetingmind.auth.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PasswordResetTokenSupportTest {

    @Test
    void issuesOpaqueResetTokensAndUsesASeparateHashDomainFromRefreshTokens() {
        AuthRuntimeProperties properties = properties();
        PasswordResetTokenSupport resetTokens = new PasswordResetTokenSupport(properties);
        RefreshTokenSupport refreshTokens = new RefreshTokenSupport(properties);

        String token = resetTokens.issue();

        assertThat(token).matches("mmpr_[A-Za-z0-9_-]{43}");
        assertThat(resetTokens.hash(token))
                .startsWith("hmac_sha256$")
                .isEqualTo(resetTokens.hash(token))
                .isNotEqualTo(refreshTokens.hash(token));
    }

    private AuthRuntimeProperties properties() {
        return new AuthRuntimeProperties(
                "test-only-refresh-hash-secret-32-bytes-minimum",
                Duration.ofDays(14),
                10,
                Duration.ofMinutes(10),
                Duration.ofMinutes(11),
                new AuthRuntimeProperties.Google(
                        List.of("meetingmind-test-client"),
                        URI.create("https://example.test/jwks"),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        Duration.ofHours(1)
                ),
                new AuthRuntimeProperties.Workload(
                        Set.of("spiffe://meetingmind.internal/ns/meetingmind/sa/meetingmind-bff"),
                        Set.of("spiffe://meetingmind.internal/ns/meetingmind/sa/meetingmind-bff"),
                        true
                ),
                new AuthRuntimeProperties.WithdrawalReconciliation(
                        false,
                        "",
                        Duration.ofSeconds(30),
                        ""
                )
        );
    }
}
