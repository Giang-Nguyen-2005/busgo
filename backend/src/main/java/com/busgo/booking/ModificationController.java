package com.busgo.booking;
import com.busgo.common.response.ApiResponse;
import com.busgo.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
public class ModificationController {
 private final ModificationService service;
 public ModificationController(ModificationService service) { this.service=service; }
 @GetMapping("/api/v1/bookings/{id}/modification-eligibility")
 public ApiResponse<?> customerEligibility(@AuthenticationPrincipal CurrentUser user,@PathVariable long id) { return ApiResponse.of(service.eligibility(user,id,false)); }
 @GetMapping("/api/v1/bookings/{id}/alternative-trips")
 public ApiResponse<?> customerAlternatives(@AuthenticationPrincipal CurrentUser user,@PathVariable long id) { return ApiResponse.of(service.alternatives(user,id,false)); }
 @GetMapping("/api/v1/bookings/{id}/modification-seat-availability")
 public ApiResponse<?> customerSeats(@AuthenticationPrincipal CurrentUser user,@PathVariable long id, @RequestParam long tripId) { return ApiResponse.of(service.seatMap(user,id,false,tripId)); }
 @PostMapping("/api/v1/bookings/{id}/modification-quotes")
 public ApiResponse<?> customerQuote(@AuthenticationPrincipal CurrentUser user,@PathVariable long id, @Valid @RequestBody ModificationDtos.Request request) { return ApiResponse.of(service.quote(user,id,false,request)); }
 @PostMapping("/api/v1/bookings/{id}/modifications")
 public ApiResponse<?> customerCreate(@AuthenticationPrincipal CurrentUser user,@PathVariable long id, @Valid @RequestBody ModificationDtos.Request request) { return ApiResponse.of(service.create(user,id,false,request)); }
 @GetMapping("/api/v1/bookings/{id}/modifications")
 public ApiResponse<?> customerHistory(@AuthenticationPrincipal CurrentUser user,@PathVariable long id) { return ApiResponse.of(service.history(user,id,false)); }
 @GetMapping("/api/v1/bookings/{id}/modifications/{mid}")
 public ApiResponse<?> customerRead(@AuthenticationPrincipal CurrentUser user,@PathVariable long id, @PathVariable long mid) { return ApiResponse.of(service.read(user,id,false,mid)); }
 @PostMapping("/api/v1/bookings/{id}/modifications/{mid}/confirm")
 public ApiResponse<?> customerConfirm(@AuthenticationPrincipal CurrentUser user,@PathVariable long id, @PathVariable long mid) { return ApiResponse.of(service.confirm(user,id,false,mid)); }
 @PostMapping("/api/v1/bookings/{id}/modifications/{mid}/cancel")
 public ApiResponse<?> customerCancel(@AuthenticationPrincipal CurrentUser user,@PathVariable long id, @PathVariable long mid) { return ApiResponse.of(service.cancel(user,id,false,mid)); }
 @GetMapping("/api/v1/operator/bookings/{id}/modification-eligibility")
 public ApiResponse<?> operatorEligibility(@AuthenticationPrincipal CurrentUser user,@PathVariable long id) { return ApiResponse.of(service.eligibility(user,id,true)); }
 @GetMapping("/api/v1/operator/bookings/{id}/alternative-trips")
 public ApiResponse<?> operatorAlternatives(@AuthenticationPrincipal CurrentUser user,@PathVariable long id) { return ApiResponse.of(service.alternatives(user,id,true)); }
 @GetMapping("/api/v1/operator/bookings/{id}/modification-seat-availability")
 public ApiResponse<?> operatorSeats(@AuthenticationPrincipal CurrentUser user,@PathVariable long id, @RequestParam long tripId) { return ApiResponse.of(service.seatMap(user,id,true,tripId)); }
 @PostMapping("/api/v1/operator/bookings/{id}/modification-quotes")
 public ApiResponse<?> operatorQuote(@AuthenticationPrincipal CurrentUser user,@PathVariable long id, @Valid @RequestBody ModificationDtos.Request request) { return ApiResponse.of(service.quote(user,id,true,request)); }
 @PostMapping("/api/v1/operator/bookings/{id}/modifications")
 public ApiResponse<?> operatorCreate(@AuthenticationPrincipal CurrentUser user,@PathVariable long id, @Valid @RequestBody ModificationDtos.Request request) { return ApiResponse.of(service.create(user,id,true,request)); }
 @GetMapping("/api/v1/operator/bookings/{id}/modifications")
 public ApiResponse<?> operatorHistory(@AuthenticationPrincipal CurrentUser user,@PathVariable long id) { return ApiResponse.of(service.history(user,id,true)); }
 @GetMapping("/api/v1/operator/bookings/{id}/modifications/{mid}")
 public ApiResponse<?> operatorRead(@AuthenticationPrincipal CurrentUser user,@PathVariable long id, @PathVariable long mid) { return ApiResponse.of(service.read(user,id,true,mid)); }
 @PostMapping("/api/v1/operator/bookings/{id}/modifications/{mid}/confirm")
 public ApiResponse<?> operatorConfirm(@AuthenticationPrincipal CurrentUser user,@PathVariable long id, @PathVariable long mid) { return ApiResponse.of(service.confirm(user,id,true,mid)); }
 @PostMapping("/api/v1/operator/bookings/{id}/modifications/{mid}/cancel")
 public ApiResponse<?> operatorCancel(@AuthenticationPrincipal CurrentUser user,@PathVariable long id, @PathVariable long mid) { return ApiResponse.of(service.cancel(user,id,true,mid)); }
}
