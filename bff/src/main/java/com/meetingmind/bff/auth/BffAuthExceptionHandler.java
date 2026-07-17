package com.meetingmind.bff.auth;

import com.meetingmind.bff.observability.BffRolloutMetrics;
import com.meetingmind.bff.proxy.BffProxyException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BffAuthExceptionHandler {

    private final BffRolloutMetrics rolloutMetrics;

    public BffAuthExceptionHandler(BffRolloutMetrics rolloutMetrics) {
        this.rolloutMetrics = rolloutMetrics;
    }

    @ExceptionHandler(BffAuthException.class)
    ResponseEntity<BffAuthErrorResponse> handleAuthException(BffAuthException exception) {
        if ("SESSION_INVALID".equals(exception.code())) {
            rolloutMetrics.recordSessionInvalid();
        }
        return ResponseEntity.status(exception.status())
                .body(error(exception.code(), exception.getMessage(), List.of()));
    }

    @ExceptionHandler(BffProxyException.class)
    ResponseEntity<BffAuthErrorResponse> handleProxyException(BffProxyException exception) {
        return ResponseEntity.status(exception.status())
                .body(error(exception.code(), exception.getMessage(), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<BffAuthErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        List<BffAuthErrorResponse.FieldError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new BffAuthErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("INVALID_REQUEST", "요청값이 잘못되었습니다.", fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<BffAuthErrorResponse> handleUnreadableRequest() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error("INVALID_REQUEST", "요청값이 잘못되었습니다.", List.of()));
    }

    private BffAuthErrorResponse error(
            String code, String message, List<BffAuthErrorResponse.FieldError> fieldErrors) {
        return new BffAuthErrorResponse(
                code,
                message,
                fieldErrors,
                UUID.randomUUID().toString().replace("-", ""));
    }
}
