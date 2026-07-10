package com.meetingmind.demo.authz;

import org.springframework.http.HttpStatus;

public enum MeetingRole {
    VIEWER(1),
    EDITOR(2),
    HOST(3);

    private final int level;

    MeetingRole(int level) {
        this.level = level;
    }

    boolean includes(MeetingRole required) {
        return level >= required.level;
    }

    public static MeetingRole parse(String value) {
        try {
            return MeetingRole.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AuthorizationException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUEST",
                    "MeetingRole은 HOST, EDITOR, VIEWER 중 하나여야 합니다."
            );
        }
    }
}
