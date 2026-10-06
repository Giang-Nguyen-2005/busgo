package com.busgo.notification;

import com.busgo.common.response.*;
import com.busgo.common.security.CurrentUser;
import com.busgo.notification.NotificationDtos.*;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/notifications","/api/v1/operator/notifications"})
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service) { this.service=service; }
    private boolean operator(jakarta.servlet.http.HttpServletRequest request) { return request.getRequestURI().startsWith("/api/v1/operator/"); }
    @GetMapping
    public PagedResponse<Notification> list(@AuthenticationPrincipal CurrentUser user,jakarta.servlet.http.HttpServletRequest request,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.list(user,operator(request),page,size);
    }
    @GetMapping("/unread-count")
    public ApiResponse<Long> count(@AuthenticationPrincipal CurrentUser user,jakarta.servlet.http.HttpServletRequest request) {
        return ApiResponse.of(service.unread(user,operator(request)));
    }
    @PatchMapping("/{id}/read")
    public ApiResponse<Boolean> mark(@AuthenticationPrincipal CurrentUser user,jakarta.servlet.http.HttpServletRequest request,@PathVariable @Positive long id) {
        service.mark(user,operator(request),id); return ApiResponse.of(true);
    }
    @PostMapping("/read-all")
    public ApiResponse<Boolean> all(@AuthenticationPrincipal CurrentUser user,jakarta.servlet.http.HttpServletRequest request) {
        service.mark(user,operator(request),null); return ApiResponse.of(true);
    }
    @GetMapping("/{id}/deliveries")
    public ApiResponse<List<Delivery>> deliveries(@AuthenticationPrincipal CurrentUser user,jakarta.servlet.http.HttpServletRequest request,@PathVariable @Positive long id) {
        return ApiResponse.of(service.deliveries(user,operator(request),id));
    }
    @PostMapping("/{id}/retry")
    public ApiResponse<Boolean> retry(@AuthenticationPrincipal CurrentUser user,jakarta.servlet.http.HttpServletRequest request,@PathVariable @Positive long id) {
        service.retry(user,operator(request),id); return ApiResponse.of(true);
    }
}
