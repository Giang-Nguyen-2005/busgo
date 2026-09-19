package com.busgo.user;

import com.busgo.auth.AuthService;
import com.busgo.auth.AuthDtos.*;
import com.busgo.common.response.ApiResponse;
import com.busgo.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {
    private final AuthService service;
    public UserController(AuthService service) { this.service=service; }
    @GetMapping public ApiResponse<Profile> me(@AuthenticationPrincipal CurrentUser user) { return new ApiResponse<>(service.profile(user)); }
    @PatchMapping public ApiResponse<Profile> update(@AuthenticationPrincipal CurrentUser user,@Valid @RequestBody ProfileRequest request) {
        return new ApiResponse<>(service.updateProfile(user,request));
    }
    @PostMapping("/change-password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal CurrentUser user,@RequestBody ChangePasswordRequest request) { service.changePassword(user,request); }
}
