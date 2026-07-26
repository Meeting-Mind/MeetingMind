package com.meetingmind.bff.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RedisIamAuthConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(RedisIamAuthConfiguration.class);

    @Test
    void leavesLocalAndTestRedisConfigurationUntouchedByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(LettuceClientConfigurationBuilderCustomizer.class);
        });
    }

    @Test
    void enablesIamCredentialsOnlyWithExplicitHostPortUsernameAndTls() {
        iamContextRunner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(LettuceClientConfigurationBuilderCustomizer.class);
        });
    }

    @Test
    void failsClosedWhenIamAuthenticationDoesNotUseTls() {
        iamContextRunner()
                .withPropertyValues("spring.data.redis.ssl.enabled=false")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessage("Redis IAM authentication requires TLS"));
    }

    @Test
    void failsClosedWhenALongLivedPasswordIsConfigured() {
        iamContextRunner()
                .withPropertyValues("spring.data.redis.password=not-allowed")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessage("Redis IAM authentication does not accept a password"));
    }

    private ApplicationContextRunner iamContextRunner() {
        return contextRunner.withPropertyValues(
                "meetingmind.bff.redis.iam-auth.enabled=true",
                "meetingmind.bff.redis.iam-auth.cache-name=test-cache",
                "meetingmind.bff.redis.iam-auth.region=ap-northeast-2",
                "spring.data.redis.host=cache.example.internal",
                "spring.data.redis.port=6379",
                "spring.data.redis.username=meetingmind-nonprod-v2-bff",
                "spring.data.redis.ssl.enabled=true");
    }
}
