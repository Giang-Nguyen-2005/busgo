package com.busgo.booking;

import com.busgo.booking.OperatorBookingDtos.*;
import com.busgo.booking.entity.BookingStatus;
import com.busgo.common.response.*;
import com.busgo.common.security.CurrentUser;
import com.busgo.payment.entity.PaymentStatus;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/operator/bookings")
public class OperatorBookingController {
    private final OperatorBookingService service;
    private final AssistedBookingService assisted;

    public OperatorBookingController(OperatorBookingService service, AssistedBookingService assisted) {
        this.service = service; this.assisted = assisted;
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ApiResponse<BookingDtos.BookingResponse> create(@AuthenticationPrincipal CurrentUser user,
            @jakarta.validation.Valid @RequestBody AssistedBookingDtos.CreateRequest request) {
        return ApiResponse.of(assisted.create(user, request));
    }
    @PostMapping("/{bookingId}/payments")
    public ApiResponse<com.busgo.payment.PaymentTicketDtos.PaymentConfirmation> record(
            @AuthenticationPrincipal CurrentUser user, @PathVariable @Positive Long bookingId,
            @jakarta.validation.Valid @RequestBody AssistedBookingDtos.RecordPayment request) {
        return ApiResponse.of(assisted.record(user, bookingId, request));
    }
    @PostMapping("/{bookingId}/payment-link")
    public ApiResponse<AssistedBookingDtos.PaymentLink> link(@AuthenticationPrincipal CurrentUser user,
            @PathVariable @Positive Long bookingId) {
        return ApiResponse.of(assisted.issueLink(user, bookingId));
    }

    @GetMapping
    public PagedResponse<OperatorBookingListItem> list(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) @Positive Long tripId,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(user, q, tripId, status, paymentStatus, date, page, size);
    }

    @GetMapping("/{bookingId}")
    public ApiResponse<OperatorBookingDetail> detail(
            @AuthenticationPrincipal CurrentUser user, @PathVariable @Positive Long bookingId) {
        return ApiResponse.of(service.detail(user, bookingId));
    }
}
