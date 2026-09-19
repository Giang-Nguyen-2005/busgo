package com.busgo.trip.search;

import com.busgo.common.response.*;
import com.busgo.trip.search.TripSearchDtos.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/trips")
public class CustomerTripController {
    private final TripSearchService service;

    public CustomerTripController(TripSearchService service) {
        this.service = service;
    }

    @GetMapping("/search")
    public PagedResponse<SearchResult> search(
            @RequestParam @Positive Long pickupLocationId,
            @RequestParam @Positive Long dropoffLocationId,
            @RequestParam @NotNull LocalDate departureDate,
            @RequestParam(required = false) @Positive Long operatorId,
            @RequestParam(required = false) @Positive Long busTypeId,
            @RequestParam(required = false) @DecimalMin("0.00") BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin("0.00") BigDecimal maxPrice,
            @RequestParam(required = false) LocalTime departureFrom,
            @RequestParam(required = false) LocalTime departureTo,
            @RequestParam(defaultValue = "DEPARTURE_ASC") SearchSort sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.search(pickupLocationId, dropoffLocationId, departureDate, operatorId,
                busTypeId, minPrice, maxPrice, departureFrom, departureTo, sort, page, size);
    }

    @GetMapping("/{tripId}")
    public ApiResponse<CustomerTripDetail> detail(@PathVariable @Positive Long tripId,
            @RequestParam @Positive Long pickupLocationId,
            @RequestParam @Positive Long dropoffLocationId) {
        return ApiResponse.of(service.detail(tripId, pickupLocationId, dropoffLocationId));
    }
}
