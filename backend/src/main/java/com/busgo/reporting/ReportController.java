package com.busgo.reporting;

import com.busgo.common.response.ApiResponse;
import com.busgo.common.security.CurrentUser;
import com.busgo.reporting.ReportDtos.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/operator/reports")
public class ReportController {
    private final ReportService service;
    public ReportController(ReportService service) { this.service=service; }
    @GetMapping("/summary")
    public ApiResponse<Summary> summary(@AuthenticationPrincipal CurrentUser user,@ModelAttribute ReportFilter filter) {
        return ApiResponse.of(service.summary(user,filter));
    }
    @GetMapping("/trips")
    public ApiResponse<Table<TripPerformance>> trips(@AuthenticationPrincipal CurrentUser user,@ModelAttribute ReportFilter filter) {
        return ApiResponse.of(service.trips(user,filter));
    }
    @GetMapping("/routes")
    public ApiResponse<Table<RoutePerformance>> routes(@AuthenticationPrincipal CurrentUser user,@ModelAttribute ReportFilter filter) {
        return ApiResponse.of(service.routes(user,filter));
    }
}
