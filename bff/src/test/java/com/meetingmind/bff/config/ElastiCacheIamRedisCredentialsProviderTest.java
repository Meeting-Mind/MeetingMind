package com.meetingmind.bff.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisCredentials;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

class ElastiCacheIamRedisCredentialsProviderTest {

    @Test
    void resolvesFreshAwsCredentialsAndSignsANewTokenForEveryConnectionAttempt() {
        AtomicInteger resolutions = new AtomicInteger();
        AwsCredentialsProvider awsCredentialsProvider = () -> AwsBasicCredentials.create(
                "AKIDEXAMPLE" + resolutions.incrementAndGet(),
                "test-secret-not-a-real-credential");
        ElastiCacheIamRedisCredentialsProvider provider =
                new ElastiCacheIamRedisCredentialsProvider(
                        "meetingmind-nonprod-v2-bff",
                        new ElastiCacheIamAuthTokenRequest(
                                "meetingmind-nonprod-v2-bff",
                                "meetingmind-nonprod-v2-valkey",
                                "ap-northeast-2"),
                        awsCredentialsProvider);

        RedisCredentials first = provider.resolveCredentials().block();
        RedisCredentials second = provider.resolveCredentials().block();

        assertThat(resolutions).hasValue(2);
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.getUsername()).isEqualTo("meetingmind-nonprod-v2-bff");
        assertThat(second.getUsername()).isEqualTo("meetingmind-nonprod-v2-bff");
        assertThat(Arrays.equals(first.getPassword(), second.getPassword())).isFalse();
        Arrays.fill(first.getPassword(), '\0');
        Arrays.fill(second.getPassword(), '\0');
    }
}
