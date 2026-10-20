package com.busgo.support;

import com.busgo.common.security.CurrentUser;
import com.busgo.common.response.PagedResponse;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated @PreAuthorize("hasAnyRole('OPERATOR_ADMIN','SYSTEM_ADMIN')")
public class AuditController {
    private final SupportService service;
    public AuditController(SupportService service) {this.service=service;}
    @GetMapping({"/api/v1/operator/audit-events","/api/v1/admin/audit-events"})
    public PagedResponse<SupportDtos.AuditEvent> list(@AuthenticationPrincipal CurrentUser u,jakarta.servlet.http.HttpServletRequest r,
        jakarta.servlet.http.HttpServletResponse response,@RequestParam(required=false) String resource,@RequestParam(required=false) @Positive Long resourceId,
        @RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        response.setHeader("Cache-Control","no-store");return service.audits(u,r.getRequestURI().contains("/admin/")?"admin":"operator",resource,resourceId,page,size);
    }
}
