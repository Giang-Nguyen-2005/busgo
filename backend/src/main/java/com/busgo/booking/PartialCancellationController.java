package com.busgo.booking;
import com.busgo.common.response.ApiResponse;
import com.busgo.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
public class PartialCancellationController {
 private final PartialCancellationService service;
 public PartialCancellationController(PartialCancellationService service) { this.service=service; }
 @GetMapping("/api/v1/bookings/{id}/partial-cancellation-eligibility")
 public ApiResponse<?> customerEligibility(@AuthenticationPrincipal CurrentUser user,@PathVariable long id) { return ApiResponse.of(service.eligibility(user,id,false)); }
 @PostMapping("/api/v1/bookings/{id}/partial-cancellation-quote")
 public ApiResponse<?> customerQuote(@AuthenticationPrincipal CurrentUser user,@PathVariable long id, @Valid @RequestBody PartialCancellationDtos.Request request) { return ApiResponse.of(service.quote(user,id,false,request)); }
 @PostMapping("/api/v1/bookings/{id}/partial-cancellations")
 public ApiResponse<?> customerExecute(@AuthenticationPrincipal CurrentUser user,@PathVariable long id, @Valid @RequestBody PartialCancellationDtos.Request request) { return ApiResponse.of(service.execute(user,id,false,request)); }
 @GetMapping("/api/v1/bookings/{id}/partial-cancellations")
 public ApiResponse<?> customerHistory(@AuthenticationPrincipal CurrentUser user,@PathVariable long id) { return ApiResponse.of(service.history(user,id,false)); }
 @GetMapping("/api/v1/bookings/{id}/partial-cancellations/{cid}")
 public ApiResponse<?> customerRead(@AuthenticationPrincipal CurrentUser user,@PathVariable long id, @PathVariable long cid) { return ApiResponse.of(service.read(user,id,false,cid)); }
 @GetMapping("/api/v1/operator/bookings/{id}/partial-cancellation-eligibility")
 public ApiResponse<?> operatorEligibility(@AuthenticationPrincipal CurrentUser user,@PathVariable long id) { return ApiResponse.of(service.eligibility(user,id,true)); }
 @PostMapping("/api/v1/operator/bookings/{id}/partial-cancellation-quote")
 public ApiResponse<?> operatorQuote(@AuthenticationPrincipal CurrentUser user,@PathVariable long id, @Valid @RequestBody PartialCancellationDtos.Request request) { return ApiResponse.of(service.quote(user,id,true,request)); }
 @PostMapping("/api/v1/operator/bookings/{id}/partial-cancellations")
 public ApiResponse<?> operatorExecute(@AuthenticationPrincipal CurrentUser user,@PathVariable long id, @Valid @RequestBody PartialCancellationDtos.Request request) { return ApiResponse.of(service.execute(user,id,true,request)); }
 @GetMapping("/api/v1/operator/bookings/{id}/partial-cancellations")
 public ApiResponse<?> operatorHistory(@AuthenticationPrincipal CurrentUser user,@PathVariable long id) { return ApiResponse.of(service.history(user,id,true)); }
 @GetMapping("/api/v1/operator/bookings/{id}/partial-cancellations/{cid}")
 public ApiResponse<?> operatorRead(@AuthenticationPrincipal CurrentUser user,@PathVariable long id, @PathVariable long cid) { return ApiResponse.of(service.read(user,id,true,cid)); }
}
