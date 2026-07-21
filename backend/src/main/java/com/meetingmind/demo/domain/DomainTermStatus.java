package com.meetingmind.demo.domain;

import com.meetingmind.demo.authz.AuthorizationException;
import java.util.Locale;
import org.springframework.http.HttpStatus;

public enum DomainTermStatus {
    ACTIVE,
    ARCHIVED;

    public static DomainTermStatus parse(String value) {
        try {
            return DomainTermStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new AuthorizationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "지원하지 않는 용어 상태입니다.");
        }
    }
}
