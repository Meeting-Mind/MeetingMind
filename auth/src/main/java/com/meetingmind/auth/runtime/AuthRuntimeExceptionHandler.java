package com.meetingmind.auth.runtime;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
class AuthRuntimeExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthRuntimeExceptionHandler.class);

    @ExceptionHandler(AuthRuntimeException.class)
    ResponseEntity<AuthApiModels.ErrorResponse> authError(
            AuthRuntimeException exception,
            HttpServletRequest request
    ) {
        return error(
                exception.status(),
                exception.code(),
                exception.getMessage(),
                List.of(),
                request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<AuthApiModels.ErrorResponse> validationError(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<AuthApiModels.FieldError> fields = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new AuthApiModels.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "요청값이 잘못되었습니다.",
                fields,
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<AuthApiModels.ErrorResponse> unreadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "요청 형식이 잘못되었습니다.",
                List.of(),
                request
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<AuthApiModels.ErrorResponse> notFound(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        return error(
                HttpStatus.NOT_FOUND,
                "NOT_FOUND",
                "요청한 경로를 찾을 수 없습니다.",
                List.of(),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<AuthApiModels.ErrorResponse> unexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        LOGGER.error(
                "auth_internal_error trace_id={} exception_type={}",
                RequestTraceFilter.current(request),
                exception.getClass().getSimpleName()
        );
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "요청을 처리할 수 없습니다.",
                List.of(),
                request
        );
    }

    private ResponseEntity<AuthApiModels.ErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            List<AuthApiModels.FieldError> fields,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(new AuthApiModels.ErrorResponse(
                        code,
                        message,
                        fields,
                        RequestTraceFilter.current(request)
                ));
    }
}
