package com.meetingmind.demo.authz;

import org.springframework.http.HttpStatus;

public enum ParticipantType {
    MEMBER,
    GUEST;

    public static ParticipantType parse(String value) {
        try {
            return ParticipantType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new AuthorizationException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUEST",
                    "participantType은 member, guest 중 하나여야 합니다."
            );
        }
    }
}
