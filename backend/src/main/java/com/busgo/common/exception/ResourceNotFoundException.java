package com.busgo.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String code, String message) {
        super(code, message, HttpStatus.NOT_FOUND, null);
    }
}
