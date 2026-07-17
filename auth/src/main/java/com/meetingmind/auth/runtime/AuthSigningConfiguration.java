package com.meetingmind.auth.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.kms.KmsClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "meetingmind.auth.signing.provider", havingValue = "aws-kms")
class AuthSigningConfiguration {

    @Bean
    KmsClient authSigningKmsClient() {
        return KmsClient.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Bean
    SigningKeyRing signingKeyRing(
            ObjectMapper objectMapper,
            AuthSigningProperties properties,
            Clock authClock
    ) {
        return SigningKeyRing.parse(objectMapper, properties.keyRingJson(), authClock);
    }

    @Bean
    AsymmetricSigningKeyProvider asymmetricSigningKeyProvider(KmsClient authSigningKmsClient) {
        return new AwsKmsSigningKeyProvider(authSigningKmsClient);
    }

    @Bean
    AccessTokenIssuer kmsAccessTokenIssuer(
            ObjectMapper objectMapper,
            AuthSigningProperties properties,
            SigningKeyRing signingKeyRing,
            AsymmetricSigningKeyProvider asymmetricSigningKeyProvider
    ) {
        return new KmsJwtAccessTokenIssuer(
                objectMapper,
                properties,
                signingKeyRing,
                asymmetricSigningKeyProvider
        );
    }

    @Bean
    JwksDocumentService jwksDocumentService(
            ObjectMapper objectMapper,
            SigningKeyRing signingKeyRing,
            AsymmetricSigningKeyProvider asymmetricSigningKeyProvider,
            Clock authClock
    ) {
        return new JwksDocumentService(
                objectMapper,
                signingKeyRing,
                asymmetricSigningKeyProvider,
                authClock
        );
    }
}
