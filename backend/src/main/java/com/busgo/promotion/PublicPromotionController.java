package com.busgo.promotion;

import com.busgo.common.response.PagedResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated @RequestMapping("/api/v1")
public class PublicPromotionController {
    private final PublicPromotionService service;
    public PublicPromotionController(PublicPromotionService service) { this.service=service; }
    @GetMapping("/public/promotions")
    public PagedResponse<PublicPromotionDtos.Promotion> list(@RequestParam(required=false) @Positive Long operatorId,
            @RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue="6") @Min(1) @Max(20) int size,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        return service.list(operatorId,page,size);
    }
    @GetMapping("/trips/{tripId}/promotions")
    public PagedResponse<PublicPromotionDtos.Promotion> trip(@PathVariable @Positive long tripId,
            @RequestParam @Positive long pickupLocationId,@RequestParam @Positive long dropoffLocationId,
            @RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue="5") @Min(1) @Max(20) int size,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        return service.trip(tripId,pickupLocationId,dropoffLocationId,page,size);
    }
}
