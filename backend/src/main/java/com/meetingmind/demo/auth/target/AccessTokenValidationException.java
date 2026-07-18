package com.meetingmind.demo.auth.target;

public final class AccessTokenValidationException extends RuntimeException {

    public AccessTokenValidationException(String message) {
        super(message);
    }

    AccessTokenValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
