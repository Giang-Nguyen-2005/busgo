package com.busgo.notification;
import com.busgo.common.response.ApiResponse;
import com.busgo.common.security.CurrentUser;
import com.busgo.notification.NotificationDtos.Preferences;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/notification-preferences")
public class NotificationPreferencesController {
    private final NotificationService service;
    public NotificationPreferencesController(NotificationService service) { this.service=service; }
    @GetMapping public ApiResponse<Preferences> get(@AuthenticationPrincipal CurrentUser user) { return ApiResponse.of(service.preferences(user)); }
    @PutMapping public ApiResponse<Preferences> put(@AuthenticationPrincipal CurrentUser user,@Valid @RequestBody Preferences preferences) { return ApiResponse.of(service.savePreferences(user,preferences)); }
}
