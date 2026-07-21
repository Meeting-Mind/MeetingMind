package com.meetingmind.demo.auth.target;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.demo.auth.AuthStore;
import com.meetingmind.demo.auth.TargetAuthUserResolver;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TargetAuthConfiguration {

    @Bean
    @ConditionalOnProperty(name = "meetingmind.core-auth.target.enabled", havingValue = "true")
    TargetAuthUserResolver targetAuthUserResolver(
            @Value("${meetingmind.core-auth.target.issuer}") String issuer,
            @Value("${meetingmind.core-auth.target.jwks-uri}") URI jwksUri,
            @Value("${meetingmind.core-auth.target.jwks-request-timeout:2s}") Duration requestTimeout,
            AuthUserMappingStore mappings,
            AuthStore authStore,
            ObjectMapper objectMapper,
            ObjectProvider<Clock> clockProvider
    ) {
        TargetAccessTokenValidator validator = new TargetAccessTokenValidator(
                issuer,
                "meetingmind-core",
                new HttpJwksSource(jwksUri, HttpClient.newHttpClient(), requestTimeout),
                objectMapper,
                clockProvider.getIfAvailable(Clock::systemUTC)
        );
        return new TargetAuthUserResolver(validator, mappings, authStore, objectMapper);
    }
}
