package com.meetingmind.demo.service;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class S3ImageStorageService {

    private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final String bucket;
    private final String region;
    private final String accessKey;
    private final String secretKey;
    private final String publicBaseUrl;

    public S3ImageStorageService(
            @Value("${S3_BUCKET:}") String bucket,
            @Value("${AWS_REGION:}") String region,
            @Value("${AWS_ACCESS_KEY:}") String accessKey,
            @Value("${AWS_SECRET_KEY:}") String secretKey,
            @Value("${S3_PUBLIC_BASE_URL:}") String publicBaseUrl
    ) {
        this.bucket = bucket;
        this.region = region;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.publicBaseUrl = publicBaseUrl;
    }

    public String uploadProfileImage(String userId, MultipartFile file) {
        return upload("profiles/" + userId, file);
    }

    public String uploadSpaceImage(String spaceId, MultipartFile file) {
        return upload("spaces/" + spaceId, file);
    }

    private String upload(String prefix, MultipartFile file) {
        validate(file);
        String contentType = file.getContentType().toLowerCase(Locale.ROOT);
        String extension = contentType.substring("image/".length());
        String key = prefix + "/" + UUID.randomUUID() + "." + extension;
        try (S3Client s3 = client()) {
            s3.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                    RequestBody.fromBytes(file.getBytes()));
            return publicUrl(key);
        } catch (Exception exception) {
            throw new ImageStorageException(HttpStatus.SERVICE_UNAVAILABLE, "이미지를 저장하지 못했습니다.", exception);
        }
    }

    private S3Client client() {
        if (bucket.isBlank() || region.isBlank() || accessKey.isBlank() || secretKey.isBlank()) {
            throw new ImageStorageException(HttpStatus.SERVICE_UNAVAILABLE, "이미지 저장소 설정이 필요합니다.");
        }
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ImageStorageException(HttpStatus.BAD_REQUEST, "이미지 파일이 필요합니다.");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ImageStorageException(HttpStatus.BAD_REQUEST, "이미지는 5MB 이하여야 합니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new ImageStorageException(HttpStatus.BAD_REQUEST, "JPEG, PNG, WebP 이미지만 업로드할 수 있습니다.");
        }
    }

    private String publicUrl(String key) {
        if (!publicBaseUrl.isBlank()) {
            return URI.create(publicBaseUrl.replaceAll("/+$", "") + "/" + key).toString();
        }
        return URI.create("https://" + bucket + ".s3." + region + ".amazonaws.com/" + key).toString();
    }

    public static final class ImageStorageException extends RuntimeException {
        private final HttpStatus status;

        ImageStorageException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        ImageStorageException(HttpStatus status, String message, Throwable cause) {
            super(message, cause);
            this.status = status;
        }

        public HttpStatus status() {
            return status;
        }
    }
}
