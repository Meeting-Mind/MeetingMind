package com.meetingmind.bff.config;

import io.lettuce.core.RedisCredentials;
import io.lettuce.core.RedisCredentialsProvider;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

final class ElastiCacheIamRedisCredentialsProvider implements RedisCredentialsProvider {

    private final String userId;
    private final ElastiCacheIamAuthTokenRequest tokenRequest;
    private final AwsCredentialsProvider awsCredentialsProvider;

    ElastiCacheIamRedisCredentialsProvider(
            String userId,
            ElastiCacheIamAuthTokenRequest tokenRequest,
            AwsCredentialsProvider awsCredentialsProvider) {
        this.userId = userId;
        this.tokenRequest = tokenRequest;
        this.awsCredentialsProvider = awsCredentialsProvider;
    }

    @Override
    public Mono<RedisCredentials> resolveCredentials() {
        return Mono.fromSupplier(() -> RedisCredentials.just(
                userId,
                tokenRequest
                        .toSignedRequestUri(awsCredentialsProvider.resolveCredentials())
                        .toCharArray()));
    }
}
