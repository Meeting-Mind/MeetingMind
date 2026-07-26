package com.meetingmind.bff.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;

class ElastiCacheIamAuthTokenRequestTest {

    @Test
    void signsNodeBasedElastiCacheConnectRequestForConfiguredCacheUserAndRegion() {
        ElastiCacheIamAuthTokenRequest request = new ElastiCacheIamAuthTokenRequest(
                "meetingmind-nonprod-v2-bff",
                "meetingmind-nonprod-v2-valkey",
                "ap-northeast-2");

        String token = request.toSignedRequestUri(
                AwsBasicCredentials.create("AKIDEXAMPLE", "test-secret-not-a-real-credential"));
        URI signedUri = URI.create("http://" + token);

        assertThat(token).doesNotStartWith("http://").doesNotContain("test-secret-not-a-real-credential");
        assertThat(signedUri.getHost()).isEqualTo("meetingmind-nonprod-v2-valkey");
        assertThat(signedUri.getRawQuery())
                .contains("Action=connect")
                .contains("User=meetingmind-nonprod-v2-bff")
                .contains("X-Amz-Expires=900")
                .contains("%2Fap-northeast-2%2Felasticache%2Faws4_request")
                .contains("X-Amz-Signature=");
    }

    @Test
    void rejectsCacheNameThatCouldChangeTheSignedRequestAuthority() {
        assertThatThrownBy(() -> new ElastiCacheIamAuthTokenRequest(
                        "meetingmind-nonprod-v2-bff",
                        "https://unexpected.example",
                        "ap-northeast-2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cache name");
    }
}
