package com.meetingmind.auth.runtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class PasswordResetDeliveryConfiguration {

    @Bean
    @ConditionalOnMissingBean(PasswordResetDelivery.class)
    PasswordResetDelivery unavailablePasswordResetDelivery() {
        return new PasswordResetDelivery() {
            @Override
            public boolean isAvailable() {
                return false;
            }

            @Override
            public void deliver(AuthModels.User user, String rawToken, java.time.Instant expiresAt) {
                throw new IllegalStateException("password reset delivery adapter가 설정되지 않았습니다.");
            }
        };
    }
}
