package com.meetingmind.bff.config;

import com.meetingmind.bff.auth.RedisRefreshSingleFlightLock;
import com.meetingmind.bff.auth.RefreshSingleFlightLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TokenManagerPolicy.class)
public class TokenManagerConfiguration {

    @Bean
    RefreshSingleFlightLock refreshSingleFlightLock(
            StringRedisTemplate redisTemplate,
            @Value("${meetingmind.bff.token-manager.lock-namespace}") String namespace) {
        return new RedisRefreshSingleFlightLock(redisTemplate, namespace);
    }
}
