package com.meetingmind.auth.runtime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("meetingmind.auth")
public record AuthRuntimeProperties(
        @NotBlank @Size(min = 32) String refreshHashSecret,
        @NotNull Duration refreshTtl,
        @Min(10) @Max(16) int passwordBcryptCost,
        @NotNull Duration recentAuthWindow,
        @NotNull Duration accessDenyWindow,
        @Valid @NotNull Google google,
        @Valid @NotNull Workload workload,
        @Valid @NotNull WithdrawalReconciliation withdrawalReconciliation
) {
    public AuthRuntimeProperties {
        if (refreshTtl != null && (refreshTtl.isNegative() || refreshTtl.isZero())) {
            throw new IllegalArgumentException("refreshTtl은 양수여야 합니다.");
        }
        if (recentAuthWindow != null && (recentAuthWindow.isNegative() || recentAuthWindow.isZero())) {
            throw new IllegalArgumentException("recentAuthWindow는 양수여야 합니다.");
        }
        if (accessDenyWindow != null && (accessDenyWindow.isNegative() || accessDenyWindow.isZero())) {
            throw new IllegalArgumentException("accessDenyWindow은 양수여야 합니다.");
        }
    }

    public record Google(
            List<String> clientIds,
            @NotNull URI jwksUri,
            @NotNull Duration connectTimeout,
            @NotNull Duration requestTimeout,
            @NotNull Duration maximumCacheTtl
    ) {
        public Google {
            clientIds = clientIds == null
                    ? List.of()
                    : clientIds.stream().map(String::trim).filter(value -> !value.isBlank()).toList();
        }
    }

    public record Workload(
            @NotEmpty Set<@NotBlank String> allowedPrincipals,
            @NotEmpty Set<@NotBlank String> jwksPrincipals,
            boolean allowTestHeader
    ) {
        public Workload {
            allowedPrincipals = allowedPrincipals == null
                    ? Set.of()
                    : allowedPrincipals.stream().map(String::trim).filter(value -> !value.isBlank()).collect(
                            java.util.stream.Collectors.toUnmodifiableSet()
                    );
            jwksPrincipals = jwksPrincipals == null
                    ? Set.of()
                    : jwksPrincipals.stream().map(String::trim).filter(value -> !value.isBlank()).collect(
                            java.util.stream.Collectors.toUnmodifiableSet()
                    );
        }
    }

    public record WithdrawalReconciliation(
            boolean enabled,
            String coreBaseUrl,
            @NotNull Duration fixedDelay,
            String testWorkloadPrincipal
    ) {
        public WithdrawalReconciliation {
            coreBaseUrl = coreBaseUrl == null ? "" : coreBaseUrl.trim();
            testWorkloadPrincipal = testWorkloadPrincipal == null ? "" : testWorkloadPrincipal.trim();
        }
    }
}
