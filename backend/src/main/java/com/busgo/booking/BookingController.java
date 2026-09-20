package com.busgo.booking;

import com.busgo.booking.BookingDtos.*;
import com.busgo.booking.entity.BookingStatus;
import com.busgo.common.response.*;
import com.busgo.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {
    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BookingResponse> create(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody CreateBookingRequest request) {
        return ApiResponse.of(service.create(user, request));
    }

    @GetMapping("/me")
    public PagedResponse<BookingListItem> mine(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.mine(user, status, page, size);
    }

    @GetMapping("/{bookingId}")
    public ApiResponse<BookingResponse> detail(@AuthenticationPrincipal CurrentUser user,
            @PathVariable @Positive Long bookingId) {
        return ApiResponse.of(service.detail(user, bookingId));
    }
}
