package com.meetingmind.auth.runtime;

import java.util.Locale;

final class ProfileImageValidator {

    static final int MAX_BYTES = 5 * 1024 * 1024;

    private ProfileImageValidator() {
    }

    static ValidatedImage validate(String declaredContentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw invalid();
        }
        String declared = declaredContentType == null ? "" : declaredContentType.toLowerCase(Locale.ROOT).trim();
        String detected = detect(bytes);
        if (detected == null || !detected.equals(declared)) {
            throw invalid();
        }
        return new ValidatedImage(detected, bytes.clone());
    }

    private static String detect(byte[] value) {
        if (value.length >= 3
                && value[0] == (byte) 0xFF
                && value[1] == (byte) 0xD8
                && value[2] == (byte) 0xFF) {
            return "image/jpeg";
        }
        if (value.length >= 8
                && value[0] == (byte) 0x89
                && value[1] == 0x50
                && value[2] == 0x4E
                && value[3] == 0x47
                && value[4] == 0x0D
                && value[5] == 0x0A
                && value[6] == 0x1A
                && value[7] == 0x0A) {
            return "image/png";
        }
        if (value.length >= 12
                && value[0] == 'R'
                && value[1] == 'I'
                && value[2] == 'F'
                && value[3] == 'F'
                && value[8] == 'W'
                && value[9] == 'E'
                && value[10] == 'B'
                && value[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static AuthRuntimeException invalid() {
        return AuthRuntimeException.badRequest(
                "PROFILE_IMAGE_INVALID",
                "프로필 사진은 5 MiB 이하의 JPEG, PNG, WebP 파일만 허용합니다.");
    }

    record ValidatedImage(String contentType, byte[] bytes) {
        ValidatedImage {
            bytes = bytes.clone();
        }
    }
}
