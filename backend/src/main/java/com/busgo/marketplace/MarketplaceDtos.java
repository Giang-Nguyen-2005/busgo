package com.busgo.marketplace;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import com.busgo.trip.search.TripSearchDtos.SearchResult;

public final class MarketplaceDtos {
    private MarketplaceDtos() {}
    public record ReviewInput(@NotNull @Min(1) @Max(5) Integer rating,
            @NotBlank @Size(max=2000) String text) {}
    public record ResponseInput(@NotBlank @Size(max=2000) String text) {}
    public record ProfileInput(@Size(max=2000) String description,
            @Size(max=500) @Pattern(regexp="^(https://[^\\s]+|/[^/\\s][^\\s]*)?$", message="Use an HTTPS or local image URL") String logoUrl) {}
    public record Operator(Long id, String name, String description, String logoUrl,
            Double averageRating, long reviewCount) {}
    public record Named(Long id, String name) {}
    public record Route(Long id, String name, Long pickupLocationId, Long dropoffLocationId) {}
    public record Profile(Operator operator, List<Route> routes, List<Named> busTypes,
            List<SearchResult> trips) {}
    public record Review(Long id, int rating, String text, String customerName,
            Long operatorId, String operatorName, String routeName,
            OffsetDateTime createdAt, OffsetDateTime updatedAt,
            String response, OffsetDateTime responseUpdatedAt) {}
    public record Eligibility(boolean eligible, String reason, Review review) {}
}
