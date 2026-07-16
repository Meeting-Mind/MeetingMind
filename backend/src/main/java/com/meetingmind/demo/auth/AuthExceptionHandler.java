package com.meetingmind.demo.auth;

import com.meetingmind.demo.authz.AuthorizationException;
import com.meetingmind.demo.observability.RequestTrace;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<AuthErrorResponse> handleAuthException(AuthException exception) {
        return ResponseEntity
                .status(exception.status())
                .body(new AuthErrorResponse(exception.code(), exception.getMessage(), List.of(), RequestTrace.currentOrCreate()));
    }

    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<AuthErrorResponse> handleAuthorizationException(AuthorizationException exception) {
        return ResponseEntity
                .status(exception.status())
                .body(new AuthErrorResponse(exception.code(), exception.getMessage(), List.of(), RequestTrace.currentOrCreate()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AuthErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        List<AuthErrorResponse.FieldError> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new AuthErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new AuthErrorResponse("INVALID_REQUEST", "요청값이 잘못되었습니다.", fieldErrors, RequestTrace.currentOrCreate()));
    }
}
