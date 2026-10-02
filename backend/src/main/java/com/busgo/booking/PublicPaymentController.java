package com.busgo.booking;

import com.busgo.common.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/payments")
public class PublicPaymentController {
    private final AssistedBookingService service;
    public PublicPaymentController(AssistedBookingService service) { this.service = service; }
    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<AssistedBookingDtos.PublicPayment>> context(@PathVariable String token) {
        return response(service.publicContext(token));
    }
    @PostMapping("/{token}/mock-confirm")
    public ResponseEntity<ApiResponse<AssistedBookingDtos.PublicPayment>> confirm(@PathVariable String token) {
        return response(service.publicConfirm(token));
    }
    private ResponseEntity<ApiResponse<AssistedBookingDtos.PublicPayment>> response(AssistedBookingDtos.PublicPayment result) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer").body(ApiResponse.of(result));
    }
}
