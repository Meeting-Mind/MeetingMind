package com.meetingmind.stt.auth;

import java.util.List;

public record AuthErrorResponse(
        String code,
        String message,
        List<FieldError> fieldErrors,
        String traceId
) {
    public record FieldError(String field, String message) {
    }
}
