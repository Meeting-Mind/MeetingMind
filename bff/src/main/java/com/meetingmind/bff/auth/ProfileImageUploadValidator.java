package com.meetingmind.bff.auth;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

final class ProfileImageUploadValidator {

    private static final int MAX_BYTES = 5 * 1024 * 1024;

    private ProfileImageUploadValidator() {
    }

    static ValidatedUpload validate(MultipartFile image) {
        try {
            byte[] bytes = image == null ? null : image.getBytes();
            String contentType = image == null ? null : image.getContentType();
            if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES || !matches(contentType, bytes)) {
                throw invalid();
            }
            return new ValidatedUpload(contentType, image.getOriginalFilename(), bytes);
        } catch (java.io.IOException exception) {
            throw invalid();
        }
    }

    private static boolean matches(String contentType, byte[] value) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
        if ("image/jpeg".equals(type)) {
            return value.length >= 3 && value[0] == (byte) 0xFF && value[1] == (byte) 0xD8 && value[2] == (byte) 0xFF;
        }
        if ("image/png".equals(type)) {
            return value.length >= 8 && value[0] == (byte) 0x89 && value[1] == 0x50 && value[2] == 0x4E && value[3] == 0x47
                    && value[4] == 0x0D && value[5] == 0x0A && value[6] == 0x1A && value[7] == 0x0A;
        }
        return "image/webp".equals(type) && value.length >= 12 && value[0] == 'R' && value[1] == 'I'
                && value[2] == 'F' && value[3] == 'F' && value[8] == 'W' && value[9] == 'E'
                && value[10] == 'B' && value[11] == 'P';
    }

    private static BffAuthException invalid() {
        return BffAuthException.of(
                HttpStatus.BAD_REQUEST,
                "PROFILE_IMAGE_INVALID",
                "프로필 사진은 5 MiB 이하의 JPEG, PNG, WebP 파일만 허용합니다.");
    }

    record ValidatedUpload(String contentType, String filename, byte[] bytes) {
    }
}
