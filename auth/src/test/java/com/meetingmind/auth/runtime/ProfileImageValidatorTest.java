package com.meetingmind.auth.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProfileImageValidatorTest {

    @Test
    void acceptsOnlyMatchingSupportedMagicBytes() {
        assertThat(ProfileImageValidator.validate("image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}).contentType())
                .isEqualTo("image/jpeg");
        assertThat(ProfileImageValidator.validate(
                        "image/png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}).contentType())
                .isEqualTo("image/png");
    }

    @Test
    void rejectsMismatchedOrOversizedContent() {
        assertThatThrownBy(() -> ProfileImageValidator.validate(
                        "image/png", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}))
                .isInstanceOf(AuthRuntimeException.class);
        assertThatThrownBy(() -> ProfileImageValidator.validate("image/jpeg", new byte[ProfileImageValidator.MAX_BYTES + 1]))
                .isInstanceOf(AuthRuntimeException.class);
    }
}
