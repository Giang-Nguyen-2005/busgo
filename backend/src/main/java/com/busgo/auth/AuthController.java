package com.busgo.auth;

import com.busgo.auth.AuthDtos.*;
import com.busgo.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service=service; }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Registration> register(@Valid @RequestBody RegisterRequest request) { return new ApiResponse<>(service.register(request)); }
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) { return new ApiResponse<>(service.login(request)); }
    @PostMapping("/refresh")
    public ApiResponse<Tokens> refresh(@RequestBody RefreshRequest request) { return new ApiResponse<>(service.refresh(request)); }
}
