package com.meetingmind.auth.runtime;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class AuthRuntimeConfiguration {

    @Bean
    Clock authClock() {
        return Clock.systemUTC();
    }

    @Bean
    TransactionTemplate authTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    @ConditionalOnProperty(
            name = "meetingmind.auth.signing.provider",
            havingValue = "disabled",
            matchIfMissing = true
    )
    AccessTokenIssuer unavailableAccessTokenIssuer() {
        return (userId, authSessionId, issuedAt) -> {
            throw AuthRuntimeException.serviceUnavailable(
                    "TOKEN_ISSUER_UNAVAILABLE",
                    "access token 발급기를 사용할 수 없습니다."
            );
        };
    }
}
