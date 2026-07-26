package com.meetingmind.bff.config;

import io.lettuce.core.RedisCredentialsProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.lettuce.RedisCredentialsProviderFactory;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({RedisIamAuthProperties.class, RedisProperties.class})
@ConditionalOnProperty(
        name = "meetingmind.bff.redis.iam-auth.enabled",
        havingValue = "true")
public class RedisIamAuthConfiguration {

    @Bean(destroyMethod = "close")
    DefaultCredentialsProvider bffRedisAwsCredentialsProvider() {
        return DefaultCredentialsProvider.builder().build();
    }

    @Bean
    LettuceClientConfigurationBuilderCustomizer redisIamCredentialsCustomizer(
            RedisIamAuthProperties iamProperties,
            RedisProperties redisProperties,
            AwsCredentialsProvider bffRedisAwsCredentialsProvider) {
        validateRedisBoundary(iamProperties, redisProperties);

        String userId = redisProperties.getUsername();
        ElastiCacheIamAuthTokenRequest tokenRequest = new ElastiCacheIamAuthTokenRequest(
                userId, iamProperties.cacheName(), iamProperties.region());
        RedisCredentialsProvider credentialsProvider = new ElastiCacheIamRedisCredentialsProvider(
                userId, tokenRequest, bffRedisAwsCredentialsProvider);
        RedisCredentialsProviderFactory credentialsProviderFactory =
                new RedisCredentialsProviderFactory() {
                    @Override
                    public RedisCredentialsProvider createCredentialsProvider(
                            RedisConfiguration redisConfiguration) {
                        return credentialsProvider;
                    }
                };

        return builder -> builder.redisCredentialsProviderFactory(credentialsProviderFactory);
    }

    private static void validateRedisBoundary(
            RedisIamAuthProperties iamProperties, RedisProperties redisProperties) {
        if (!iamProperties.enabled()) {
            throw new IllegalArgumentException("Redis IAM authentication must be enabled");
        }
        if (!redisProperties.getSsl().isEnabled()) {
            throw new IllegalArgumentException("Redis IAM authentication requires TLS");
        }
        if (!StringUtils.hasText(redisProperties.getHost())
                || redisProperties.getPort() < 1
                || redisProperties.getPort() > 65_535) {
            throw new IllegalArgumentException("Redis IAM host and port must be valid");
        }
        if (!StringUtils.hasText(redisProperties.getUsername())) {
            throw new IllegalArgumentException("Redis IAM username must not be blank");
        }
        if (StringUtils.hasText(redisProperties.getPassword())) {
            throw new IllegalArgumentException("Redis IAM authentication does not accept a password");
        }
        if (StringUtils.hasText(redisProperties.getUrl())) {
            throw new IllegalArgumentException("Redis IAM authentication requires explicit host and port");
        }
    }
}
