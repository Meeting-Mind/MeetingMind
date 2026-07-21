package com.meetingmind.bff.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ProfileImageUploadValidatorTest {

    @Test
    void acceptsMatchingPngUpload() {
        ProfileImageUploadValidator.ValidatedUpload upload = ProfileImageUploadValidator.validate(
                new MockMultipartFile(
                        "image", "profile.png", "image/png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}));

        assertThat(upload.contentType()).isEqualTo("image/png");
    }

    @Test
    void rejectsDeclaredMimeThatDoesNotMatchMagicBytes() {
        assertThatThrownBy(() -> ProfileImageUploadValidator.validate(
                        new MockMultipartFile(
                                "image", "profile.png", "image/png", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})))
                .isInstanceOf(BffAuthException.class)
                .extracting(error -> ((BffAuthException) error).code())
                .isEqualTo("PROFILE_IMAGE_INVALID");
    }
}
