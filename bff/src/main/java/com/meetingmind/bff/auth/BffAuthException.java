package com.meetingmind.bff.auth;

import org.springframework.http.HttpStatus;

public final class BffAuthException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private BffAuthException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static BffAuthException of(HttpStatus status, String code, String message) {
        return new BffAuthException(status, code, message);
    }
}
