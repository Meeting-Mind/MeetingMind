package com.meetingmind.auth.runtime;

import org.springframework.http.HttpStatus;

final class AuthRuntimeException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private AuthRuntimeException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    HttpStatus status() {
        return status;
    }

    String code() {
        return code;
    }

    static AuthRuntimeException badRequest(String code, String message) {
        return new AuthRuntimeException(HttpStatus.BAD_REQUEST, code, message);
    }

    static AuthRuntimeException unauthorized(String code, String message) {
        return new AuthRuntimeException(HttpStatus.UNAUTHORIZED, code, message);
    }

    static AuthRuntimeException forbidden(String code, String message) {
        return new AuthRuntimeException(HttpStatus.FORBIDDEN, code, message);
    }

    static AuthRuntimeException conflict(String code, String message) {
        return new AuthRuntimeException(HttpStatus.CONFLICT, code, message);
    }

    static AuthRuntimeException serviceUnavailable(String code, String message) {
        return new AuthRuntimeException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }
}
