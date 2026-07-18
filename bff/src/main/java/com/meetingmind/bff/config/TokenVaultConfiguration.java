package com.meetingmind.bff.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.bff.tokenvault.EncryptedTokenBundleStore;
import com.meetingmind.bff.tokenvault.RedisEncryptedTokenBundleStore;
import com.meetingmind.bff.tokenvault.TokenVault;
import com.meetingmind.bff.tokenvault.crypto.AesGcmTokenPayloadCipher;
import com.meetingmind.bff.tokenvault.crypto.AwsKmsEnvelopeKeyService;
import com.meetingmind.bff.tokenvault.crypto.EnvelopeKeyService;
import com.meetingmind.bff.tokenvault.crypto.LocalEnvelopeKeyService;
import com.meetingmind.bff.tokenvault.crypto.TokenPayloadCipher;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.kms.KmsClient;

@Configuration(proxyBeanMethods = false)
public class TokenVaultConfiguration {

    @Bean
    Clock tokenVaultClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnProperty(name = "meetingmind.bff.token-vault.key-provider", havingValue = "local")
    EnvelopeKeyService localEnvelopeKeyService(
            @Value("${meetingmind.bff.token-vault.local-key-id}") String keyId,
            @Value("${meetingmind.bff.token-vault.local-master-key-base64}") String masterKeyBase64) {
        return new LocalEnvelopeKeyService(keyId, masterKeyBase64);
    }

    @Bean
    @ConditionalOnProperty(
            name = "meetingmind.bff.token-vault.key-provider",
            havingValue = "kms",
            matchIfMissing = true)
    KmsClient tokenVaultKmsClient() {
        return KmsClient.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Bean
    @ConditionalOnProperty(
            name = "meetingmind.bff.token-vault.key-provider",
            havingValue = "kms",
            matchIfMissing = true)
    EnvelopeKeyService awsKmsEnvelopeKeyService(
            KmsClient tokenVaultKmsClient,
            @Value("${meetingmind.bff.token-vault.kms-key-id}") String kmsKeyId) {
        return new AwsKmsEnvelopeKeyService(tokenVaultKmsClient, kmsKeyId);
    }

    @Bean
    TokenPayloadCipher tokenPayloadCipher(EnvelopeKeyService envelopeKeyService) {
        return new AesGcmTokenPayloadCipher(envelopeKeyService);
    }

    @Bean
    EncryptedTokenBundleStore encryptedTokenBundleStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            Clock tokenVaultClock,
            @Value("${meetingmind.bff.token-vault.namespace}") String namespace) {
        return new RedisEncryptedTokenBundleStore(redisTemplate, objectMapper, tokenVaultClock, namespace);
    }

    @Bean
    TokenVault tokenVault(
            EncryptedTokenBundleStore store,
            TokenPayloadCipher payloadCipher,
            ObjectMapper objectMapper,
            Clock tokenVaultClock) {
        return new TokenVault(store, payloadCipher, objectMapper, tokenVaultClock);
    }
}
