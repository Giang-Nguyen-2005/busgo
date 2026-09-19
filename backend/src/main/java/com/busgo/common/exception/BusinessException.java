package com.busgo.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    private final Object details;

    public BusinessException(String code, String message) {
        this(code, message, HttpStatus.CONFLICT, null);
    }

    public BusinessException(String code, String message, HttpStatus status, Object details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = details;
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
    public Object getDetails() { return details; }
}
