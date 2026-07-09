package com.meetingmind.demo.authz;

import org.springframework.http.HttpStatus;

public enum SpaceRole {
    OWNER,
    ADMIN,
    MEMBER;

    public static SpaceRole parse(String value) {
        try {
            return SpaceRole.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AuthorizationException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUEST",
                    "SpaceRole은 OWNER, ADMIN, MEMBER 중 하나여야 합니다."
            );
        }
    }
}
