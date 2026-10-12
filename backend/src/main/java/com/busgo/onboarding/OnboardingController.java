package com.busgo.onboarding;

import static com.busgo.onboarding.OnboardingDtos.*;
import com.busgo.common.response.*;
import com.busgo.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated @RequestMapping("/api/v1")
public class OnboardingController {
    private final OnboardingService service;
    public OnboardingController(OnboardingService service) { this.service=service; }
    @ModelAttribute
    public void privateResponse(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        response.setHeader("Referrer-Policy","no-referrer");
    }
    @PostMapping("/public/operator-applications") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ApiResponse<?> submit(@Valid @RequestBody ApplicationRequest r) { return ApiResponse.of(service.submit(r)); }
    @GetMapping("/public/operator-invitations/{token}/context")
    public ApiResponse<?> context(@PathVariable String token) { return ApiResponse.of(service.context(token)); }
    @PostMapping("/public/operator-invitations/{token}/accept")
    public ApiResponse<?> accept(@PathVariable String token,@Valid @RequestBody Acceptance r) { return ApiResponse.of(service.accept(token,r)); }
    @GetMapping("/admin/dashboard")
    public ApiResponse<?> dashboard(@AuthenticationPrincipal CurrentUser u) { return ApiResponse.of(service.dashboard(u)); }
    @GetMapping("/admin/operator-applications")
    public PagedResponse<Map<String,Object>> applications(@AuthenticationPrincipal CurrentUser u,
        @RequestParam(required=false) ApplicationStatus status,@RequestParam(required=false) @Size(max=100) String q,
        @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.applications(u,status,q,page,size);
    }
    @GetMapping("/admin/operator-applications/{id}")
    public ApiResponse<?> detail(@AuthenticationPrincipal CurrentUser u,@PathVariable @Positive long id) { return ApiResponse.of(service.detail(u,id)); }
    @PostMapping("/admin/operator-applications/{id}/review")
    public ApiResponse<?> review(@AuthenticationPrincipal CurrentUser u,@PathVariable @Positive long id,@Valid @RequestBody Decision r) { return ApiResponse.of(service.review(u,id,r)); }
    @PostMapping("/admin/operator-applications/{id}/approve")
    public ApiResponse<?> approve(@AuthenticationPrincipal CurrentUser u,@PathVariable @Positive long id,@Valid @RequestBody Decision r) { return ApiResponse.of(service.approve(u,id,r)); }
    @PostMapping("/admin/operator-applications/{id}/reject")
    public ApiResponse<?> reject(@AuthenticationPrincipal CurrentUser u,@PathVariable @Positive long id,@Valid @RequestBody Reason r) { return ApiResponse.of(service.reject(u,id,r)); }
    @PostMapping("/admin/operator-applications/{id}/invitation/reissue")
    public ApiResponse<?> reissue(@AuthenticationPrincipal CurrentUser u,@PathVariable @Positive long id) { return ApiResponse.of(service.reissue(u,id)); }
    @GetMapping("/admin/operators/{id}/governance")
    public ApiResponse<?> governance(@AuthenticationPrincipal CurrentUser u,@PathVariable @Positive long id) { return ApiResponse.of(service.governance(u,id)); }
    @PostMapping("/admin/operators/{id}/suspend")
    public ApiResponse<?> suspend(@AuthenticationPrincipal CurrentUser u,@PathVariable @Positive long id,@Valid @RequestBody Reason r) { return ApiResponse.of(service.status(u,id,r,true)); }
    @PostMapping("/admin/operators/{id}/reactivate")
    public ApiResponse<?> reactivate(@AuthenticationPrincipal CurrentUser u,@PathVariable @Positive long id,@Valid @RequestBody Reason r) { return ApiResponse.of(service.status(u,id,r,false)); }
}
