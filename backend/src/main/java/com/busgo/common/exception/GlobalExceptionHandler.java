package com.busgo.common.exception;

import com.busgo.common.response.ApiError;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                details.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                details.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        return new ResponseEntity<>(ApiError.of("VALIDATION_ERROR", "Request validation failed.", details), headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        HttpStatus httpStatus = HttpStatus.resolve(status.value());
        String code = httpStatus == null ? "HTTP_ERROR" : httpStatus.name();
        String message = httpStatus == null ? "Request failed." : httpStatus.getReasonPhrase();
        return new ResponseEntity<>(ApiError.of(code, message, null), headers, status);
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> business(BusinessException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiError.of(ex.getCode(), ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> unauthorized(AuthenticationException ex) {
        if (ex instanceof com.busgo.common.security.AuthenticationFailure failure)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of(failure.getCode(), failure.getMessage(), null));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of("UNAUTHORIZED", "Authentication is required.", null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("ACCESS_DENIED", "Access is denied.", null));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex) {
        // Avoid logging request data or exception messages that may contain secrets.
        log.error("Unhandled API exception of type {}", ex.getClass().getName());
        return ResponseEntity.internalServerError()
                .body(ApiError.of("INTERNAL_SERVER_ERROR", "An unexpected error occurred.", null));
    }
}
