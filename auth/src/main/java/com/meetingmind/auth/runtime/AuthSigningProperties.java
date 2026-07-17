package com.meetingmind.auth.runtime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("meetingmind.auth.signing")
public record AuthSigningProperties(
        @NotBlank String provider,
        @NotNull URI issuer,
        String keyRingJson
) {
    public AuthSigningProperties {
        provider = provider == null ? "" : provider.trim();
        keyRingJson = keyRingJson == null ? "" : keyRingJson.trim();
        if (!provider.equals("disabled") && !provider.equals("aws-kms")) {
            throw new IllegalArgumentException("signing provider는 disabled 또는 aws-kms여야 합니다.");
        }
        if (issuer == null || !issuer.isAbsolute()) {
            throw new IllegalArgumentException("signing issuer는 절대 URI여야 합니다.");
        }
        if (provider.equals("aws-kms") && keyRingJson.isBlank()) {
            throw new IllegalArgumentException("AWS KMS signing key ring이 필요합니다.");
        }
    }
}
