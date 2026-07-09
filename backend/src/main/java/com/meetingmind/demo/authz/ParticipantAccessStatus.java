package com.meetingmind.demo.authz;

import org.springframework.http.HttpStatus;

public enum ParticipantAccessStatus {
    ACTIVE,
    REVOKED;

    public static ParticipantAccessStatus parse(String value) {
        try {
            return ParticipantAccessStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AuthorizationException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUEST",
                    "MeetingParticipant.accessStatus는 ACTIVE, REVOKED 중 하나여야 합니다."
            );
        }
    }
}
