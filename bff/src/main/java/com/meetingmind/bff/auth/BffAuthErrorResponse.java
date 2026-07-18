package com.meetingmind.bff.auth;

import java.util.List;

public record BffAuthErrorResponse(
        String code,
        String message,
        List<FieldError> fieldErrors,
        String traceId) {

    public record FieldError(String field, String message) {}
}
