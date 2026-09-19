package com.busgo.trip.search;

import static com.busgo.common.time.BusGoTime.*;

import com.busgo.common.exception.*;
import com.busgo.common.response.PagedResponse;
import com.busgo.trip.entity.*;
import com.busgo.trip.repository.*;
import com.busgo.trip.search.TripSearchDtos.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripSearchService {
    private final TripSearchRepository search;
    private final TripStopSnapshotRepository stops;
    private final CustomerJourneyResolver journeys;
    private final SeatAvailabilityQueryRepository availability;
    private final Clock clock;

    public TripSearchService(TripSearchRepository search, TripStopSnapshotRepository stops,
            CustomerJourneyResolver journeys, SeatAvailabilityQueryRepository availability,
            Clock clock) {
        this.search = search;
        this.stops = stops;
        this.journeys = journeys;
        this.availability = availability;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PagedResponse<SearchResult> search(Long pickupLocationId, Long dropoffLocationId,
            LocalDate departureDate, Long operatorId, Long busTypeId, BigDecimal minPrice,
            BigDecimal maxPrice, LocalTime departureFrom, LocalTime departureTo,
            SearchSort sort, int page, int size) {
        validateJourneyIds(pickupLocationId, dropoffLocationId);
        validateFilters(minPrice, maxPrice, departureFrom, departureTo);
        var window = businessDate(departureDate);
        var criteria = new TripSearchRepository.Criteria(pickupLocationId, dropoffLocationId,
                window.startInclusive(), window.endExclusive(), utc(clock.instant()), operatorId,
                busTypeId, minPrice, maxPrice,
                departureFrom == null ? null : businessTime(departureDate, departureFrom),
                departureTo == null ? null : businessTime(departureDate, departureTo), sort);
        return PagedResponse.from(search.search(criteria, PageRequest.of(page, size)));
    }

    @Transactional(readOnly = true)
    public CustomerTripDetail detail(Long tripId, Long pickupLocationId, Long dropoffLocationId) {
        var journey = journeys.resolve(tripId, pickupLocationId, dropoffLocationId);
        Trip trip = journey.trip();
        TripStop pickup = journey.pickup();
        TripStop dropoff = journey.dropoff();
        long available = availability.availableCounts(Set.of(tripId), pickupLocationId,
                dropoffLocationId).getOrDefault(tripId, 0L);
        if (available == 0) throw notBookable("No seat is available for the selected journey.");

        var operator = trip.getOperatorRoute().getOperator();
        var route = trip.getOperatorRoute().getRoute();
        var busType = trip.getBus().getBusType();
        List<CustomerTripStop> stopResponses = stops.findByTripIdOrderByStopOrderAsc(tripId).stream()
                .map(this::stop).toList();
        long duration = ChronoUnit.MINUTES.between(pickup.getPlannedDepartureTime(),
                dropoff.getPlannedArrivalTime());
        return new CustomerTripDetail(tripId,
                new OperatorSummary(operator.getId(), operator.getName()),
                new RouteSummary(route.getId(), route.getName()),
                new BusTypeSummary(busType.getId(), busType.getName()),
                new PickupSummary(pickup.getId(), pickup.getLocation().getId(),
                        pickup.getLocation().getName(), api(pickup.getPlannedDepartureTime())),
                new DropoffSummary(dropoff.getId(), dropoff.getLocation().getId(),
                        dropoff.getLocation().getName(), api(dropoff.getPlannedArrivalTime())),
                duration, journey.fare().getPrice(), available, trip.getStatus(), stopResponses);
    }

    private CustomerTripStop stop(TripStop stop) {
        return new CustomerTripStop(stop.getId(), stop.getLocation().getId(), stop.getLocation().getName(),
                stop.getStopOrder(), stop.isAllowPickup(), stop.isAllowDropoff(),
                api(stop.getPlannedArrivalTime()), api(stop.getPlannedDepartureTime()));
    }

    private static void validateJourneyIds(Long pickup, Long dropoff) {
        if (pickup.equals(dropoff)) {
            throw invalid("INVALID_ROUTE_DIRECTION", "Pickup and dropoff locations must be different.");
        }
    }

    private static void validateFilters(BigDecimal minPrice, BigDecimal maxPrice,
            LocalTime from, LocalTime to) {
        if (minPrice != null && minPrice.signum() < 0 || maxPrice != null && maxPrice.signum() < 0
                || minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw invalid("INVALID_PRICE_RANGE", "Price range is invalid.");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw invalid("INVALID_DEPARTURE_RANGE", "Departure time range is invalid.");
        }
    }

    private static BusinessException invalid(String code, String message) {
        return new BusinessException(code, message, HttpStatus.BAD_REQUEST, null);
    }

    private static BusinessException notBookable(String message) {
        return new BusinessException("TRIP_NOT_BOOKABLE", message, HttpStatus.CONFLICT, null);
    }
}
