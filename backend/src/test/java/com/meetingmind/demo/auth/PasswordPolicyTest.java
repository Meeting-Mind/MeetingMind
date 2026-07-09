package com.meetingmind.demo.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsPasswordWithAtLeastThreeCharacterCategories() {
        assertThat(PasswordPolicy.isValid("Password1")).isTrue();
        assertThat(PasswordPolicy.isValid("password1!")).isTrue();
    }

    @Test
    void rejectsPasswordWithFewerThanThreeCharacterCategories() {
        assertThat(PasswordPolicy.isValid("password")).isFalse();
        assertThat(PasswordPolicy.isValid("password1")).isFalse();
        assertThat(PasswordPolicy.isValid("PASSWORD!")).isFalse();
    }
}
