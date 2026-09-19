package com.busgo.auth;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Locale;
import com.busgo.user.entity.RoleCode;

public final class AuthDtos {
    private AuthDtos() {}
    static String email(String value) { return value == null ? null : value.strip().toLowerCase(Locale.ROOT); }
    public record RegisterRequest(@NotBlank @Size(max=100) String fullName,
            @NotBlank @Email @Size(max=150) String email, @NotBlank @Size(max=20) String phone, String password) {
        public RegisterRequest { email = AuthDtos.email(email); }
        @Override public String toString() { return "RegisterRequest[REDACTED]"; }
    }
    public record LoginRequest(@NotBlank @Email @Size(max=150) String email, String password) {
        public LoginRequest { email = AuthDtos.email(email); }
        @Override public String toString() { return "LoginRequest[REDACTED]"; }
    }
    public record RefreshRequest(String refreshToken) {
        @Override public String toString() { return "RefreshRequest[REDACTED]"; }
    }
    public record ChangePasswordRequest(String currentPassword, String newPassword) {
        @Override public String toString() { return "ChangePasswordRequest[REDACTED]"; }
    }
    public record ProfileRequest(@Size(min=1,max=100) @Pattern(regexp="(?s).*\\S.*") String fullName,
            @Size(min=1,max=20) @Pattern(regexp="(?s).*\\S.*") String phone) {}
    public record Registration(Long id, String fullName, String email, String phone, RoleCode role) {}
    public record Profile(Long id, String fullName, String email, String phone, List<RoleCode> roles) {}
    public record LoginUser(Long id, String fullName, String email, List<RoleCode> roles) {}
    public record Tokens(String accessToken, String refreshToken, long expiresIn) {
        @Override public String toString() { return "Tokens[REDACTED]"; }
    }
    public record LoginResponse(String accessToken, String refreshToken, long expiresIn, LoginUser user) {
        @Override public String toString() { return "LoginResponse[REDACTED]"; }
    }
}
