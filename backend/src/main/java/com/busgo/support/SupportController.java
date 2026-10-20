package com.busgo.support;

import static com.busgo.support.SupportDtos.*;
import com.busgo.common.response.*;
import com.busgo.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated
@RequestMapping({"/api/v1/support","/api/v1/operator/support","/api/v1/admin/disputes"})
@PreAuthorize("hasAnyRole('CUSTOMER','OPERATOR_STAFF','OPERATOR_ADMIN','SYSTEM_ADMIN')")
public class SupportController {
    private final SupportService service;
    public SupportController(SupportService service) { this.service=service; }
    private String area(jakarta.servlet.http.HttpServletRequest r) {return r.getRequestURI().startsWith("/api/v1/admin/")?"admin":r.getRequestURI().startsWith("/api/v1/operator/")?"operator":"customer";}
    @ModelAttribute public void noStore(jakarta.servlet.http.HttpServletResponse r) {r.setHeader("Cache-Control","no-store");}
    @GetMapping("/tickets")
    public PagedResponse<Ticket> list(@AuthenticationPrincipal CurrentUser u,jakarta.servlet.http.HttpServletRequest r,
        @RequestParam(required=false) State status,@RequestParam(required=false) String queue,@RequestParam(required=false) String disputeStatus,
        @RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {return service.list(u,area(r),status,queue,disputeStatus,page,size);}
    @PostMapping("/tickets") @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<Ticket> create(@AuthenticationPrincipal CurrentUser u,jakarta.servlet.http.HttpServletRequest r,@Valid @RequestBody Create c) {
        if(!area(r).equals("customer")) throw new org.springframework.security.access.AccessDeniedException("Customer workspace required");return ApiResponse.of(service.create(u,c));
    }
    @GetMapping("/tickets/{id}")
    public ApiResponse<Ticket> detail(@AuthenticationPrincipal CurrentUser u,jakarta.servlet.http.HttpServletRequest r,@PathVariable @Positive long id) {return ApiResponse.of(service.detail(u,area(r),id));}
    @GetMapping("/tickets/{id}/messages")
    public PagedResponse<Activity> messages(@AuthenticationPrincipal CurrentUser u,jakarta.servlet.http.HttpServletRequest r,@PathVariable @Positive long id,
        @RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {return service.messages(u,area(r),id,page,size);}
    @PostMapping("/tickets/{id}/{action}")
    public ApiResponse<Ticket> action(@AuthenticationPrincipal CurrentUser u,jakarta.servlet.http.HttpServletRequest r,@PathVariable @Positive long id,
        @PathVariable @Pattern(regexp="messages|claim|assign|priority|transition|resolve|escalate|reopen|accept-resolution|appeal|review|request-information|decision") String action,@Valid @RequestBody Command c) {return ApiResponse.of(service.command(u,area(r),id,action,c));}
    @GetMapping("/dashboard")
    public ApiResponse<java.util.List<Counter>> dashboard(@AuthenticationPrincipal CurrentUser u,jakarta.servlet.http.HttpServletRequest r) {return ApiResponse.of(service.dashboard(u,area(r)));}
    @GetMapping("/notifications")
    public PagedResponse<Notice> inbox(@AuthenticationPrincipal CurrentUser u,jakarta.servlet.http.HttpServletRequest r,
        @RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {return service.inbox(u,area(r),page,size);}
    @PostMapping("/notifications/read-all")
    public ApiResponse<Boolean> mark(@AuthenticationPrincipal CurrentUser u,jakarta.servlet.http.HttpServletRequest r) {service.markInbox(u,area(r));return ApiResponse.of(true);}
    @GetMapping("/assignees") @PreAuthorize("hasRole('OPERATOR_ADMIN')")
    public ApiResponse<java.util.List<Assignee>> assignees(@AuthenticationPrincipal CurrentUser u) {return ApiResponse.of(service.assignees(u));}
    @GetMapping("/tickets/{id}/dispute") @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Dispute> dispute(@AuthenticationPrincipal CurrentUser u,@PathVariable @Positive long id) {return ApiResponse.of(service.dispute(u,id));}
}
