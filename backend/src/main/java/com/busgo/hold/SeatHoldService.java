package com.busgo.hold;

import static com.busgo.common.time.BusGoTime.*;
import static com.busgo.hold.SeatHoldDtos.*;

import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.exception.*;
import com.busgo.common.security.CurrentUser;
import com.busgo.hold.SeatHoldInventoryRepository.*;
import com.busgo.route.entity.OperatorRouteFare;
import com.busgo.route.repository.OperatorRouteFareRepository;
import com.busgo.trip.entity.*;
import com.busgo.trip.repository.*;
import com.busgo.trip.search.CustomerJourneyResolver;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeatHoldService {
    private final CustomerJourneyResolver journeys;
    private final TripSeatRepository seats;
    private final TripRepository trips;
    private final TripStopSnapshotRepository stops;
    private final OperatorRouteFareRepository fares;
    private final SeatHoldInventoryRepository inventory;
    private final SeatHoldProperties properties;
    private final Clock clock;

    public SeatHoldService(CustomerJourneyResolver journeys, TripSeatRepository seats,
            TripRepository trips, TripStopSnapshotRepository stops,
            OperatorRouteFareRepository fares, SeatHoldInventoryRepository inventory,
            SeatHoldProperties properties, Clock clock) {
        this.journeys = journeys;
        this.seats = seats;
        this.trips = trips;
        this.stops = stops;
        this.fares = fares;
        this.inventory = inventory;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public SeatHoldResponse create(CurrentUser user, CreateSeatHoldRequest request) {
        List<Long> seatIds = normalizeSeatIds(request.tripSeatIds());
        var journey = journeys.resolve(request.tripId(), request.pickupLocationId(),
                request.dropoffLocationId());
        List<TripSeat> requestedSeats = seats.findByTripIdAndIdInOrderByIdAsc(
                request.tripId(), seatIds);
        if (requestedSeats.size() != seatIds.size()) throw unavailable();

        List<Long> segmentIds = journey.requiredSegments().stream()
                .sorted(Comparator.comparing(TripSegment::getSegmentOrder))
                .map(TripSegment::getId).toList();
        LocalDateTime now = utc(clock.instant());
        LocalDateTime expiresAt = now.plus(properties.seatHoldDuration());
        List<LockedInventory> locked = inventory.lockRequired(seatIds, segmentIds);
        int expected = Math.multiplyExact(seatIds.size(), segmentIds.size());
        if (locked.size() != expected || locked.stream().anyMatch(row -> !reclaimable(row, now))) {
            throw unavailable();
        }

        String token = UUID.randomUUID().toString();
        int updated = inventory.markHeld(locked.stream().map(LockedInventory::id).toList(),
                token, user.id(), expiresAt, now);
        if (updated != expected) throw unavailable();
        return response(token, journey.trip(), journey.pickup(), journey.dropoff(),
                heldSeats(requestedSeats), journey.fare().getPrice(), expiresAt, HoldStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public SeatHoldResponse get(CurrentUser user, String token) {
        List<HoldInventory> rows = inventory.findOwnedHold(token, user.id());
        HoldShape shape = validateShape(rows);
        Trip trip = trips.findPublicById(shape.tripId())
                .orElseThrow(SeatHoldService::notFound);
        TripStop pickup = stops.findById(shape.pickupTripStopId())
                .orElseThrow(SeatHoldService::notFound);
        TripStop dropoff = stops.findById(shape.dropoffTripStopId())
                .orElseThrow(SeatHoldService::notFound);
        OperatorRouteFare fare = currentFare(trip, pickup, dropoff);
        LocalDateTime now = utc(clock.instant());
        HoldStatus status = shape.expiresAt().isAfter(now)
                ? HoldStatus.ACTIVE : HoldStatus.EXPIRED;
        List<HeldSeat> heldSeats = shape.seats().entrySet().stream()
                .map(entry -> new HeldSeat(entry.getKey(), entry.getValue())).toList();
        return response(token, trip, pickup, dropoff, heldSeats, fare.getPrice(),
                shape.expiresAt(), status);
    }

    @Transactional
    public void release(CurrentUser user, String token) {
        inventory.releaseOwned(token, user.id(), utc(clock.instant()));
    }

    private List<Long> normalizeSeatIds(List<Long> input) {
        if (input.size() > properties.maxSeatsPerHold()) {
            throw validation("A hold may contain at most " + properties.maxSeatsPerHold() + " seats.");
        }
        List<Long> sorted = input.stream().sorted().toList();
        if (new HashSet<>(sorted).size() != sorted.size()) {
            throw validation("Duplicate trip seat IDs are not allowed.");
        }
        return sorted;
    }

    private static boolean reclaimable(LockedInventory row, LocalDateTime now) {
        if (row.status() == InventoryStatus.AVAILABLE) return true;
        return row.status() == InventoryStatus.HELD && row.holdExpiresAt() != null
                && !row.holdExpiresAt().isAfter(now);
    }

    private OperatorRouteFare currentFare(Trip trip, TripStop pickup, TripStop dropoff) {
        if (pickup.getSourceRouteStop() == null || dropoff.getSourceRouteStop() == null) {
            throw notFound();
        }
        return fares.findExactActiveFare(trip.getOperatorRoute().getId(),
                        pickup.getSourceRouteStop().getId(), dropoff.getSourceRouteStop().getId(),
                        ActiveStatus.ACTIVE)
                .filter(fare -> fare.getPrice() != null && fare.getPrice().signum() > 0)
                .orElseThrow(SeatHoldService::notFound);
    }

    private static HoldShape validateShape(List<HoldInventory> rows) {
        if (rows.isEmpty() || rows.stream().anyMatch(row -> row.status() != InventoryStatus.HELD
                || row.holdExpiresAt() == null)) throw notFound();
        Long tripId = rows.get(0).tripId();
        LocalDateTime expiry = rows.get(0).holdExpiresAt();
        if (rows.stream().anyMatch(row -> !tripId.equals(row.tripId())
                || !expiry.equals(row.holdExpiresAt()))) throw notFound();

        LinkedHashMap<Long, String> heldSeats = rows.stream().collect(Collectors.toMap(
                HoldInventory::tripSeatId, HoldInventory::seatCode, (left, right) -> left,
                LinkedHashMap::new));
        List<HoldInventory> segmentRows = rows.stream()
                .collect(Collectors.toMap(HoldInventory::tripSegmentId, Function.identity(),
                        (left, right) -> left, TreeMap::new)).values().stream()
                .sorted(Comparator.comparing(HoldInventory::segmentOrder)).toList();
        if (segmentRows.isEmpty() || rows.size() != heldSeats.size() * segmentRows.size()) {
            throw notFound();
        }
        for (int index = 1; index < segmentRows.size(); index++) {
            HoldInventory previous = segmentRows.get(index - 1);
            HoldInventory current = segmentRows.get(index);
            if (current.segmentOrder() != previous.segmentOrder() + 1
                    || !previous.toTripStopId().equals(current.fromTripStopId())) throw notFound();
        }
        return new HoldShape(tripId, segmentRows.get(0).fromTripStopId(),
                segmentRows.get(segmentRows.size() - 1).toTripStopId(), expiry, heldSeats);
    }

    private static List<HeldSeat> heldSeats(List<TripSeat> seats) {
        return seats.stream().sorted(Comparator.comparing(TripSeat::getId))
                .map(seat -> new HeldSeat(seat.getId(), seat.getSeatCode())).toList();
    }

    private static SeatHoldResponse response(String token, Trip trip, TripStop pickup,
            TripStop dropoff, List<HeldSeat> seats, BigDecimal price, LocalDateTime expiresAt,
            HoldStatus status) {
        List<HeldSeat> selected = seats.stream()
                .sorted(Comparator.comparing(HeldSeat::tripSeatId)).toList();
        BigDecimal total = price.multiply(BigDecimal.valueOf(selected.size()));
        return new SeatHoldResponse(token, trip.getId(),
                new Pickup(pickup.getId(), pickup.getLocation().getId(),
                        pickup.getLocation().getName(), api(pickup.getPlannedDepartureTime())),
                new Dropoff(dropoff.getId(), dropoff.getLocation().getId(),
                        dropoff.getLocation().getName(), api(dropoff.getPlannedArrivalTime())),
                selected.stream().map(HeldSeat::tripSeatId).toList(), selected,
                price, total, api(expiresAt), status);
    }

    private static BusinessException validation(String message) {
        return new BusinessException("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST,
                Map.of("tripSeatIds", message));
    }

    private static BusinessException unavailable() {
        return new BusinessException("SEAT_NOT_AVAILABLE",
                "One or more selected seats are no longer available.", HttpStatus.CONFLICT, null);
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("SEAT_HOLD_NOT_FOUND", "Seat hold was not found.");
    }

    private record HoldShape(Long tripId, Long pickupTripStopId, Long dropoffTripStopId,
            LocalDateTime expiresAt, LinkedHashMap<Long, String> seats) {}
}
