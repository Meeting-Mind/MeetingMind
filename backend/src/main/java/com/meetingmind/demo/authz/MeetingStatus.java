package com.meetingmind.demo.authz;

import org.springframework.http.HttpStatus;

public enum MeetingStatus {
    SCHEDULED,
    IN_PROGRESS,
    ENDED,
    CANCELED;

    public static MeetingStatus parse(String value) {
        try {
            return MeetingStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AuthorizationException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUEST",
                    "MeetingStatus는 SCHEDULED, IN_PROGRESS, ENDED, CANCELED 중 하나여야 합니다."
            );
        }
    }
}
