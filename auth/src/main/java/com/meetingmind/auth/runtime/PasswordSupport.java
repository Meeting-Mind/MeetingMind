package com.meetingmind.auth.runtime;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class PasswordSupport {

    static final String POLICY_MESSAGE =
            "비밀번호는 8~128자이며 영대문자, 영소문자, 숫자, 특수문자 중 3종 이상을 포함해야 합니다.";

    private final BCryptPasswordEncoder encoder;

    PasswordSupport(AuthRuntimeProperties properties) {
        this.encoder = new BCryptPasswordEncoder(properties.passwordBcryptCost());
    }

    String hash(String password) {
        return encoder.encode(password);
    }

    boolean matches(String password, String hash) {
        return hash != null && encoder.matches(password, hash);
    }

    boolean isValid(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            return false;
        }
        int categories = 0;
        if (password.chars().anyMatch(Character::isUpperCase)) {
            categories++;
        }
        if (password.chars().anyMatch(Character::isLowerCase)) {
            categories++;
        }
        if (password.chars().anyMatch(Character::isDigit)) {
            categories++;
        }
        if (password.chars().anyMatch(codePoint -> !Character.isLetterOrDigit(codePoint))) {
            categories++;
        }
        return categories >= 3;
    }
}
