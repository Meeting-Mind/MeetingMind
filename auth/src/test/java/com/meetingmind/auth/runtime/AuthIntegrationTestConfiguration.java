package com.meetingmind.auth.runtime;

import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class AuthIntegrationTestConfiguration {

    @Bean
    @Primary
    AccessTokenIssuer integrationAccessTokenIssuer() {
        return (userId, authSessionId, issuedAt) -> List.of(
                token("meetingmind-core", userId, authSessionId),
                token("meetingmind-ai", userId, authSessionId),
                token("meetingmind-livekit", userId, authSessionId)
        );
    }

    @Bean
    @Primary
    GoogleCredentialVerifier integrationGoogleCredentialVerifier() {
        return credential -> {
            String prefix = "valid-google:";
            if (!credential.startsWith(prefix)) {
                throw AuthRuntimeException.unauthorized(
                        "GOOGLE_CREDENTIAL_INVALID",
                        "Google credential이 올바르지 않습니다."
                );
            }
            String email = credential.substring(prefix.length());
            return new AuthModels.GoogleUser(
                    "google-sub-" + Integer.toUnsignedString(email.hashCode()),
                    email,
                    "Google Test User",
                    null
            );
        };
    }

    private AccessTokenIssuer.IssuedAccessToken token(
            String audience,
            java.util.UUID userId,
            java.util.UUID authSessionId
    ) {
        return new AccessTokenIssuer.IssuedAccessToken(
                audience,
                "test." + audience + "." + userId + "." + authSessionId,
                600
        );
    }
}
