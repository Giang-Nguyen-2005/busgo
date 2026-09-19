package com.busgo.common.security;

import org.springframework.security.core.AuthenticationException;

public class AuthenticationFailure extends AuthenticationException {
    private final String code;

    public AuthenticationFailure(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }

    public static AuthenticationFailure credentials() {
        return new AuthenticationFailure("INVALID_CREDENTIALS", "Invalid credentials.");
    }

    public static AuthenticationFailure refresh() {
        return new AuthenticationFailure("REFRESH_TOKEN_INVALID", "Refresh token is invalid or expired.");
    }
}
