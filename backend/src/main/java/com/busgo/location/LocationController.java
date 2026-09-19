package com.busgo.location;

import com.busgo.common.response.ApiResponse;
import com.busgo.location.LocationDtos.LocationResponse;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {
    private final LocationService service;

    public LocationController(LocationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<LocationResponse>> search(
            @RequestParam(defaultValue = "") @Size(max = 150) String q) {
        return ApiResponse.of(service.search(q));
    }
}
