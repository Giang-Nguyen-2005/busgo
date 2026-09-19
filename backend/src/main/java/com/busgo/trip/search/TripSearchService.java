package com.busgo.trip.search;

import static com.busgo.common.time.BusGoTime.*;

import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.exception.*;
import com.busgo.common.response.PagedResponse;
import com.busgo.route.entity.OperatorRouteFare;
import com.busgo.route.repository.OperatorRouteFareRepository;
import com.busgo.trip.entity.*;
import com.busgo.trip.repository.*;
import com.busgo.trip.search.TripSearchDtos.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripSearchService {
    private final TripSearchRepository search;
    private final TripRepository trips;
    private final TripStopSnapshotRepository stops;
    private final TripSeatSegmentInventoryRepository inventory;
    private final OperatorRouteFareRepository fares;
    private final Clock clock;

    public TripSearchService(TripSearchRepository search, TripRepository trips,
            TripStopSnapshotRepository stops, TripSeatSegmentInventoryRepository inventory,
            OperatorRouteFareRepository fares, Clock clock) {
        this.search = search;
        this.trips = trips;
        this.stops = stops;
        this.inventory = inventory;
        this.fares = fares;
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
        validateJourneyIds(pickupLocationId, dropoffLocationId);
        Trip trip = trips.findPublicById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("TRIP_NOT_FOUND", "Trip was not found."));
        TripStop pickup = stops.findByTripIdAndLocationId(tripId, pickupLocationId)
                .orElseThrow(() -> invalid("INVALID_PICKUP_STOP", "Pickup stop is not part of this trip."));
        TripStop dropoff = stops.findByTripIdAndLocationId(tripId, dropoffLocationId)
                .orElseThrow(() -> invalid("INVALID_DROPOFF_STOP", "Dropoff stop is not part of this trip."));
        if (!pickup.isAllowPickup() || pickup.getPlannedDepartureTime() == null) {
            throw invalid("INVALID_PICKUP_STOP", "Pickup is not allowed at the selected stop.");
        }
        if (!dropoff.isAllowDropoff() || dropoff.getPlannedArrivalTime() == null) {
            throw invalid("INVALID_DROPOFF_STOP", "Dropoff is not allowed at the selected stop.");
        }
        if (pickup.getStopOrder() >= dropoff.getStopOrder()) {
            throw invalid("INVALID_ROUTE_DIRECTION", "Pickup must occur before dropoff.");
        }
        if (trip.getStatus() != TripStatus.SCHEDULED
                || !pickup.getPlannedDepartureTime().isAfter(utc(clock.instant()))) {
            throw notBookable("Trip is not searchable for the selected journey.");
        }
        if (pickup.getSourceRouteStop() == null || dropoff.getSourceRouteStop() == null) {
            throw notBookable("The selected journey has no exact active fare.");
        }
        OperatorRouteFare fare = fares.findExactActiveFare(trip.getOperatorRoute().getId(),
                        pickup.getSourceRouteStop().getId(), dropoff.getSourceRouteStop().getId(),
                        ActiveStatus.ACTIVE)
                .filter(item -> item.getPrice() != null && item.getPrice().signum() > 0)
                .orElseThrow(() -> notBookable("The selected journey has no exact active fare."));
        long available = inventory.countFullyAvailableSeats(tripId, pickup.getStopOrder(),
                dropoff.getStopOrder(), dropoff.getStopOrder() - pickup.getStopOrder());
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
                duration, fare.getPrice(), available, trip.getStatus(), stopResponses);
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
