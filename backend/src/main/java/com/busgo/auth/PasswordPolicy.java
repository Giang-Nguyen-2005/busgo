package com.busgo.auth;

import java.nio.charset.StandardCharsets;
import com.busgo.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class PasswordPolicy {
    private PasswordPolicy() {}
    public static boolean bcryptCompatible(String password) {
        return password != null && !password.isBlank() && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
    public static void validate(String password) {
        if (!bcryptCompatible(password) || password.codePointCount(0, password.length()) < 8)
            throw new BusinessException("INVALID_PASSWORD", "Password must contain at least 8 characters and at most 72 UTF-8 bytes.", HttpStatus.BAD_REQUEST, null);
    }
}
