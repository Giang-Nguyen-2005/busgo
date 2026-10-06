package com.busgo.marketplace;

import com.busgo.marketplace.MarketplaceDtos.*;
import com.busgo.common.response.*;
import com.busgo.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;

@RestController
@Validated
@RequestMapping("/api/v1")
public class MarketplaceController {
    private final MarketplaceService service;
    public MarketplaceController(MarketplaceService service) { this.service=service; }
    @GetMapping({"/public/operators","/public/discovery/operators"})
    public PagedResponse<Operator> operators(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="12") @Min(1) @Max(100) int size) { return service.operators(page,size); }
    @GetMapping("/public/operators/{id}")
    public ApiResponse<Operator> operator(@PathVariable @Positive long id) { return ApiResponse.of(service.operator(id)); }
    @GetMapping("/public/operators/{id}/profile")
    public ApiResponse<Profile> profile(@PathVariable @Positive long id) { return ApiResponse.of(service.profile(id)); }
    @GetMapping("/public/operators/{id}/reviews")
    public PagedResponse<Review> reviews(@PathVariable @Positive long id,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="10") @Min(1) @Max(100) int size) { return service.reviews(id,page,size); }
    @GetMapping("/public/reviews/recent")
    public PagedResponse<Review> recent(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="6") @Min(1) @Max(100) int size) { return service.reviews(null,page,size); }
    @GetMapping("/public/discovery/routes")
    public ApiResponse<java.util.List<Route>> routes() { return ApiResponse.of(service.routes()); }
    @GetMapping("/public/discovery/trips")
    public ApiResponse<java.util.List<com.busgo.trip.search.TripSearchDtos.SearchResult>> upcoming() { return ApiResponse.of(service.upcoming(null)); }
    @GetMapping("/public/discovery/bus-types")
    public ApiResponse<java.util.List<Named>> types() { return ApiResponse.of(service.busTypes()); }
    @GetMapping("/bookings/{id}/review-eligibility")
    public ApiResponse<Eligibility> eligibility(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long id) { return ApiResponse.of(service.eligibility(user,id)); }
    @PostMapping("/bookings/{id}/review") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Review> create(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long id,@Valid @RequestBody ReviewInput input) { return ApiResponse.of(service.create(user,id,input)); }
    @PutMapping("/bookings/{id}/review")
    public ApiResponse<Review> edit(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long id,@Valid @RequestBody ReviewInput input) { return ApiResponse.of(service.edit(user,id,input)); }
    @DeleteMapping("/bookings/{id}/review")
    public ApiResponse<Boolean> delete(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long id) { return ApiResponse.of(service.delete(user,id)); }
    @GetMapping("/operator/marketplace-profile")
    public ApiResponse<Operator> own(@AuthenticationPrincipal CurrentUser user) { return ApiResponse.of(service.ownProfile(user)); }
    @PutMapping("/operator/marketplace-profile")
    public ApiResponse<Operator> update(@AuthenticationPrincipal CurrentUser user,@Valid @RequestBody ProfileInput input) { return ApiResponse.of(service.updateProfile(user,input)); }
    @GetMapping("/operator/reviews")
    public PagedResponse<Review> ownReviews(@AuthenticationPrincipal CurrentUser user,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="10") @Min(1) @Max(100) int size) { return service.ownReviews(user,page,size); }
    @PutMapping("/operator/reviews/{id}/response")
    public ApiResponse<Boolean> respond(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long id,@Valid @RequestBody ResponseInput input) { return ApiResponse.of(service.respond(user,id,input)); }
}
