package com.meetingmind.auth.runtime;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("meetingmind.auth.profile-image-storage")
public record ProfileImageStorageProperties(
        boolean enabled,
        String bucket,
        String region,
        URI endpoint,
        String accessKey,
        String secretKey,
        boolean pathStyleAccess,
        boolean createBucketIfMissing) {

    public ProfileImageStorageProperties {
        bucket = normalize(bucket);
        region = normalize(region);
        accessKey = normalize(accessKey);
        secretKey = normalize(secretKey);
        if (enabled && (bucket.isBlank() || region.isBlank())) {
            throw new IllegalArgumentException("profile image storage가 활성화되면 bucket과 region이 필요합니다.");
        }
        if ((accessKey.isBlank()) != (secretKey.isBlank())) {
            throw new IllegalArgumentException("profile image storage access key와 secret key는 함께 설정해야 합니다.");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
