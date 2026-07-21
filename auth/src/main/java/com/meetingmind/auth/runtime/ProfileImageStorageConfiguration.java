package com.meetingmind.auth.runtime;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@Configuration(proxyBeanMethods = false)
class ProfileImageStorageConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "meetingmind.auth.profile-image-storage", name = "enabled", havingValue = "true")
    S3Client profileImageS3Client(ProfileImageStorageProperties properties) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .forcePathStyle(properties.pathStyleAccess());
        if (properties.endpoint() != null) {
            builder.endpointOverride(properties.endpoint());
        }
        if (properties.accessKey().isBlank()) {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        } else {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "meetingmind.auth.profile-image-storage", name = "enabled", havingValue = "true")
    ProfileImageStorage s3ProfileImageStorage(S3Client client, ProfileImageStorageProperties properties) {
        ensureBucket(client, properties);
        return new S3ProfileImageStorage(client, properties.bucket());
    }

    @Bean
    @ConditionalOnMissingBean(ProfileImageStorage.class)
    ProfileImageStorage unavailableProfileImageStorage() {
        return new ProfileImageStorage() {
            @Override public boolean isAvailable() { return false; }
            @Override public String store(UUID userId, ProfileImageValidator.ValidatedImage image) {
                throw unavailable();
            }
            @Override public void delete(String objectKey) { throw unavailable(); }
            @Override public boolean isManagedKey(String value) { return false; }
        };
    }

    private static void ensureBucket(S3Client client, ProfileImageStorageProperties properties) {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
        } catch (S3Exception exception) {
            if (!properties.createBucketIfMissing() || exception.statusCode() != 404) {
                throw exception;
            }
            client.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
        }
    }

    private static AuthRuntimeException unavailable() {
        return AuthRuntimeException.serviceUnavailable(
                "PROFILE_IMAGE_STORAGE_UNAVAILABLE",
                "프로필 사진 저장소를 사용할 수 없습니다.");
    }

    private static final class S3ProfileImageStorage implements ProfileImageStorage {

        private final S3Client client;
        private final String bucket;

        private S3ProfileImageStorage(S3Client client, String bucket) {
            this.client = client;
            this.bucket = bucket;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String store(UUID userId, ProfileImageValidator.ValidatedImage image) {
            String key = "profile-images/" + userId + "/" + UUID.randomUUID();
            client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).contentType(image.contentType()).build(),
                    RequestBody.fromBytes(image.bytes()));
            return key;
        }

        @Override
        public void delete(String objectKey) {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
        }

        @Override
        public boolean isManagedKey(String value) {
            return value != null && value.startsWith("profile-images/");
        }
    }
}
