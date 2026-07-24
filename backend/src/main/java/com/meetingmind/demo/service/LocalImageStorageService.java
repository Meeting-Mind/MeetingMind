package com.meetingmind.demo.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalImageStorageService {

    private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final Path uploadRoot;

    public LocalImageStorageService(
            @Value("${MEETINGMIND_IMAGE_UPLOAD_DIR:.local-uploads/images}") String uploadDir
    ) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public String uploadProfileImage(String userId, MultipartFile file) {
        return upload("profiles", userId, file);
    }

    public String uploadSpaceImage(String spaceId, MultipartFile file) {
        return upload("spaces", spaceId, file);
    }

    public Resource image(String category, String ownerId, String filename) {
        validateSegment(category);
        validateSegment(ownerId);
        validateSegment(filename);
        try {
            Path imagePath = uploadRoot.resolve(category).resolve(ownerId).resolve(filename).normalize();
            if (!imagePath.startsWith(uploadRoot) || !Files.isRegularFile(imagePath)) {
                throw new ImageStorageException(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다.");
            }
            return new UrlResource(imagePath.toUri());
        } catch (IOException exception) {
            throw new ImageStorageException(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다.", exception);
        }
    }

    private String upload(String category, String ownerId, MultipartFile file) {
        validate(file);
        validateSegment(category);
        validateSegment(ownerId);

        String contentType = file.getContentType().toLowerCase(Locale.ROOT);
        String extension = extension(contentType);
        String filename = UUID.randomUUID() + "." + extension;
        Path ownerDir = uploadRoot.resolve(category).resolve(ownerId).normalize();
        Path imagePath = ownerDir.resolve(filename).normalize();
        if (!ownerDir.startsWith(uploadRoot) || !imagePath.startsWith(ownerDir)) {
            throw new ImageStorageException(HttpStatus.BAD_REQUEST, "이미지 저장 경로가 올바르지 않습니다.");
        }

        try {
            Files.createDirectories(ownerDir);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, imagePath);
            }
            return "/api/v1/assets/images/" + category + "/" + ownerId + "/" + filename;
        } catch (IOException exception) {
            throw new ImageStorageException(HttpStatus.SERVICE_UNAVAILABLE, "이미지를 저장하지 못했습니다.", exception);
        }
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new ImageStorageException(HttpStatus.BAD_REQUEST, "JPEG, PNG, WebP 이미지만 업로드할 수 있습니다.");
        };
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

    private void validateSegment(String value) {
        if (value == null || value.isBlank() || value.contains("/") || value.contains("\\") || value.contains("..")) {
            throw new ImageStorageException(HttpStatus.BAD_REQUEST, "이미지 경로가 올바르지 않습니다.");
        }
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
