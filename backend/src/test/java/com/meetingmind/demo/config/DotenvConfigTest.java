package com.meetingmind.demo.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DotenvConfigTest {

    @Test
    void throwsWhenNoKeyIsConfigured() {
        assertThatThrownBy(() -> DotenvConfig.require("DOES_NOT_EXIST_KEY_FOR_TEST"))
                .isInstanceOf(IllegalStateException.class);
    }
}
