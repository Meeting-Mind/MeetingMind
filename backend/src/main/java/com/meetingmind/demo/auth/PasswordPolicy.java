package com.meetingmind.demo.auth;

final class PasswordPolicy {

    static final String MESSAGE = "비밀번호는 최소 8자이며 영대문자, 영소문자, 숫자, 특수문자 중 3종 이상을 포함해야 합니다.";

    private PasswordPolicy() {
    }

    static boolean isValid(String password) {
        if (password == null || password.length() < 8) {
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
        if (password.chars().anyMatch((codePoint) -> !Character.isLetterOrDigit(codePoint))) {
            categories++;
        }

        return categories >= 3;
    }
}
