package com.meetingmind.demo.authz;

import org.springframework.http.HttpStatus;

public class AuthorizationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AuthorizationException(HttpStatus status, String code, String message) {
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
}
