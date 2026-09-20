package com.busgo.payment;

import com.busgo.common.response.ApiResponse;
import com.busgo.common.security.CurrentUser;
import com.busgo.payment.PaymentTicketDtos.*;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/bookings/{bookingId}")
public class PaymentTicketController {
    private final PaymentTicketService service;

    public PaymentTicketController(PaymentTicketService service) {
        this.service = service;
    }

    @PostMapping("/payments/mock-confirm")
    public ApiResponse<PaymentConfirmation> confirm(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable @Positive Long bookingId) {
        return ApiResponse.of(service.confirm(user, bookingId));
    }

    @GetMapping("/ticket")
    public ApiResponse<TicketBundle> ticket(@AuthenticationPrincipal CurrentUser user,
            @PathVariable @Positive Long bookingId) {
        return ApiResponse.of(service.ticket(user, bookingId));
    }
}
