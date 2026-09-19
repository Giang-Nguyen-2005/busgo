package com.busgo.common.health;

import com.busgo.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/api/v1/health")
    public ApiResponse<HealthStatus> health() {
        return ApiResponse.of(new HealthStatus("UP"));
    }

    public record HealthStatus(String status) {}
}
