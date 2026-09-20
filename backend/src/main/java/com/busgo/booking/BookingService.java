package com.busgo.booking;

import static com.busgo.booking.BookingDtos.*;
import static com.busgo.common.time.BusGoTime.api;
import static com.busgo.common.time.BusGoTime.utc;

import com.busgo.booking.BookingInventoryRepository.LockedHoldRow;
import com.busgo.booking.entity.*;
import com.busgo.booking.repository.*;
import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.exception.BusinessException;
import com.busgo.common.exception.ResourceNotFoundException;
import com.busgo.common.response.PagedResponse;
import com.busgo.common.security.CurrentUser;
import com.busgo.route.entity.OperatorRouteFare;
import com.busgo.route.repository.OperatorRouteFareRepository;
import com.busgo.trip.entity.*;
import com.busgo.trip.repository.*;
import com.busgo.trip.search.TripSegmentResolver;
import com.busgo.user.entity.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
    private final BookingRepository bookings;
    private final BookingItemRepository items;
    private final BookingInventoryRepository inventory;
    private final TripRepository trips;
    private final TripStopSnapshotRepository stops;
    private final TripSegmentResolver segmentResolver;
    private final OperatorRouteFareRepository fares;
    private final EntityManager entityManager;
    private final Clock clock;

    public BookingService(BookingRepository bookings, BookingItemRepository items,
            BookingInventoryRepository inventory, TripRepository trips,
            TripStopSnapshotRepository stops, TripSegmentResolver segmentResolver,
            OperatorRouteFareRepository fares, EntityManager entityManager, Clock clock) {
        this.bookings = bookings;
        this.items = items;
        this.inventory = inventory;
        this.trips = trips;
        this.stops = stops;
        this.segmentResolver = segmentResolver;
        this.fares = fares;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional
    public BookingResponse create(CurrentUser currentUser, CreateBookingRequest request) {
        LocalDateTime now = utc(clock.instant());
        List<LockedHoldRow> rows = inventory.lockOwnedHold(request.holdToken(), currentUser.id());
        if (rows.isEmpty()) throw holdNotFound();
        HoldShape hold = validateHold(rows, request.holdToken(), currentUser.id(), now);

        Trip trip = trips.findPublicById(hold.tripId()).orElseThrow(BookingService::holdCorrupt);
        TripStop pickup = stops.findById(hold.pickupTripStopId())
                .orElseThrow(BookingService::holdCorrupt);
        TripStop dropoff = stops.findById(hold.dropoffTripStopId())
                .orElseThrow(BookingService::holdCorrupt);
        List<TripSegment> required = validateJourney(trip, pickup, dropoff, hold.segmentIds(), now);
        OperatorRouteFare fare = currentFare(trip, pickup, dropoff);
        BigDecimal unitPrice = fare.getPrice();

        Booking booking = new Booking();
        booking.setBookingCode(newBookingCode());
        booking.setCustomer(entityManager.getReference(User.class, currentUser.id()));
        booking.setTrip(trip);
        booking.setPickupTripStop(pickup);
        booking.setDropoffTripStop(dropoff);
        booking.setContactName(request.contactName());
        booking.setContactPhone(request.contactPhone());
        booking.setContactEmail(request.contactEmail());
        booking.setStatus(BookingStatus.PENDING);
        booking.setTotalAmount(unitPrice.multiply(BigDecimal.valueOf(hold.seats().size())));
        booking = bookings.saveAndFlush(booking);

        List<BookingItem> createdItems = new ArrayList<>();
        for (var seat : hold.seats().entrySet()) {
            BookingItem item = new BookingItem();
            item.setBooking(booking);
            item.setTripSeat(entityManager.getReference(TripSeat.class, seat.getKey()));
            item.setSeatCode(seat.getValue());
            item.setUnitPrice(unitPrice);
            createdItems.add(item);
        }
        createdItems = items.saveAllAndFlush(createdItems);

        Map<Long, BookingItem> itemBySeat = createdItems.stream().collect(Collectors.toMap(
                item -> item.getTripSeat().getId(), Function.identity()));
        for (var seat : hold.seats().entrySet()) {
            List<Long> inventoryIds = rows.stream()
                    .filter(row -> row.tripSeatId().equals(seat.getKey()))
                    .map(LockedHoldRow::id).toList();
            BookingItem item = itemBySeat.get(seat.getKey());
            if (inventory.convertToBooked(inventoryIds, item.getId(), request.holdToken(),
                    currentUser.id(), now) != required.size()) {
                throw holdCorrupt();
            }
        }
        booking.setItems(createdItems);
        return response(booking);
    }

    @Transactional(readOnly = true)
    public PagedResponse<BookingListItem> mine(CurrentUser user, BookingStatus status,
            int page, int size) {
        Page<Booking> result = bookings.findOwned(user.id(), status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PagedResponse<>(result.getContent().stream().map(this::listItem).toList(),
                new PagedResponse.Pagination(result.getNumber(), result.getSize(),
                        result.getTotalElements(), result.getTotalPages()));
    }

    @Transactional(readOnly = true)
    public BookingResponse detail(CurrentUser user, Long bookingId) {
        return response(bookings.findOwnedById(bookingId, user.id())
                .orElseThrow(BookingService::bookingNotFound));
    }

    private HoldShape validateHold(List<LockedHoldRow> rows, String token, Long userId,
            LocalDateTime now) {
        if (rows.stream().anyMatch(row -> row.holdExpiresAt() == null)) throw holdCorrupt();
        LocalDateTime expiry = rows.get(0).holdExpiresAt();
        if (!expiry.isAfter(now)) throw holdExpired();
        Long tripId = rows.get(0).tripId();
        if (rows.stream().anyMatch(row -> row.status() != InventoryStatus.HELD
                || !token.equals(row.holdToken()) || !userId.equals(row.heldByUserId())
                || row.bookingItemId() != null || !tripId.equals(row.tripId())
                || !expiry.equals(row.holdExpiresAt()))) throw holdCorrupt();

        LinkedHashMap<Long, String> seats = rows.stream().collect(Collectors.toMap(
                LockedHoldRow::tripSeatId, LockedHoldRow::seatCode, (left, right) -> left,
                LinkedHashMap::new));
        List<LockedHoldRow> segmentRows = rows.stream().collect(Collectors.toMap(
                LockedHoldRow::tripSegmentId, Function.identity(), (left, right) -> left,
                TreeMap::new)).values().stream()
                .sorted(Comparator.comparing(LockedHoldRow::segmentOrder)).toList();
        if (seats.isEmpty() || segmentRows.isEmpty()
                || rows.size() != Math.multiplyExact(seats.size(), segmentRows.size())) {
            throw holdCorrupt();
        }
        Set<Long> expectedSegments = segmentRows.stream()
                .map(LockedHoldRow::tripSegmentId).collect(Collectors.toSet());
        for (Long seatId : seats.keySet()) {
            Set<Long> actual = rows.stream().filter(row -> row.tripSeatId().equals(seatId))
                    .map(LockedHoldRow::tripSegmentId).collect(Collectors.toSet());
            if (!actual.equals(expectedSegments)) throw holdCorrupt();
        }
        for (int index = 1; index < segmentRows.size(); index++) {
            LockedHoldRow previous = segmentRows.get(index - 1);
            LockedHoldRow current = segmentRows.get(index);
            if (current.segmentOrder() != previous.segmentOrder() + 1
                    || !previous.toTripStopId().equals(current.fromTripStopId())) {
                throw holdCorrupt();
            }
        }
        return new HoldShape(tripId, segmentRows.get(0).fromTripStopId(),
                segmentRows.get(segmentRows.size() - 1).toTripStopId(), seats,
                segmentRows.stream().map(LockedHoldRow::tripSegmentId).toList());
    }

    private List<TripSegment> validateJourney(Trip trip, TripStop pickup, TripStop dropoff,
            List<Long> heldSegmentIds, LocalDateTime now) {
        if (!pickup.getTrip().getId().equals(trip.getId())
                || !dropoff.getTrip().getId().equals(trip.getId())
                || !pickup.isAllowPickup() || pickup.getPlannedDepartureTime() == null
                || !dropoff.isAllowDropoff() || dropoff.getPlannedArrivalTime() == null
                || pickup.getStopOrder() >= dropoff.getStopOrder()) throw holdCorrupt();
        if (trip.getStatus() != TripStatus.SCHEDULED
                || !pickup.getPlannedDepartureTime().isAfter(now)) {
            throw new BusinessException("TRIP_NOT_BOOKABLE",
                    "Trip is not bookable for the held journey.", HttpStatus.CONFLICT, null);
        }
        List<TripSegment> required = segmentResolver.resolve(trip.getId(), pickup, dropoff);
        if (!required.stream().map(TripSegment::getId).toList().equals(heldSegmentIds)) {
            throw holdCorrupt();
        }
        return required;
    }

    private OperatorRouteFare currentFare(Trip trip, TripStop pickup, TripStop dropoff) {
        if (pickup.getSourceRouteStop() == null || dropoff.getSourceRouteStop() == null) {
            throw missingFare();
        }
        return fares.findExactActiveFare(trip.getOperatorRoute().getId(),
                        pickup.getSourceRouteStop().getId(), dropoff.getSourceRouteStop().getId(),
                        ActiveStatus.ACTIVE)
                .filter(value -> value.getPrice() != null && value.getPrice().signum() > 0)
                .orElseThrow(BookingService::missingFare);
    }

    private String newBookingCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = "BG-" + UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 16).toUpperCase(Locale.ROOT);
            if (!bookings.existsByBookingCode(code)) return code;
        }
        throw new BusinessException("BOOKING_CODE_UNAVAILABLE",
                "A booking code could not be generated.", HttpStatus.CONFLICT, null);
    }

    private BookingResponse response(Booking booking) {
        List<BookingSeat> bookingSeats = booking.getItems().stream()
                .map(item -> new BookingSeat(item.getTripSeat().getId(), item.getSeatCode(),
                        item.getPassengerName(), item.getUnitPrice())).toList();
        Trip trip = booking.getTrip();
        TripStop pickup = booking.getPickupTripStop();
        TripStop dropoff = booking.getDropoffTripStop();
        return new BookingResponse(booking.getId(), booking.getBookingCode(), booking.getStatus(),
                trip.getId(), new OperatorSummary(trip.getOperatorRoute().getOperator().getId(),
                        trip.getOperatorRoute().getOperator().getName()),
                new RouteSummary(trip.getOperatorRoute().getRoute().getId(),
                        trip.getOperatorRoute().getRoute().getName()),
                stop(pickup, pickup.getPlannedDepartureTime()),
                stop(dropoff, dropoff.getPlannedArrivalTime()),
                new Contact(booking.getContactName(), booking.getContactPhone(),
                        booking.getContactEmail()), bookingSeats,
                bookingSeats.get(0).unitPrice(), booking.getTotalAmount(),
                api(booking.getCreatedAt()));
    }

    private BookingListItem listItem(Booking booking) {
        Trip trip = booking.getTrip();
        TripStop pickup = booking.getPickupTripStop();
        TripStop dropoff = booking.getDropoffTripStop();
        return new BookingListItem(booking.getId(), booking.getBookingCode(), booking.getStatus(),
                trip.getId(), trip.getOperatorRoute().getRoute().getName(),
                trip.getOperatorRoute().getOperator().getName(),
                stop(pickup, pickup.getPlannedDepartureTime()),
                stop(dropoff, dropoff.getPlannedArrivalTime()),
                api(pickup.getPlannedDepartureTime()),
                booking.getItems().stream().map(BookingItem::getSeatCode).toList(),
                booking.getTotalAmount(), api(booking.getCreatedAt()));
    }

    private StopSummary stop(TripStop stop, LocalDateTime time) {
        return new StopSummary(stop.getId(), stop.getLocation().getId(),
                stop.getLocation().getName(), api(time));
    }

    private static ResourceNotFoundException holdNotFound() {
        return new ResourceNotFoundException("SEAT_HOLD_NOT_FOUND", "Seat hold was not found.");
    }

    private static BusinessException holdExpired() {
        return new BusinessException("SEAT_HOLD_EXPIRED", "Seat hold has expired.",
                HttpStatus.CONFLICT, null);
    }

    private static BusinessException holdCorrupt() {
        return new BusinessException("SEAT_NOT_AVAILABLE",
                "The held seat inventory is no longer available.", HttpStatus.CONFLICT, null);
    }

    private static BusinessException missingFare() {
        return new BusinessException("TRIP_NOT_BOOKABLE",
                "The held journey has no exact active fare.", HttpStatus.CONFLICT, null);
    }

    private static ResourceNotFoundException bookingNotFound() {
        return new ResourceNotFoundException("BOOKING_NOT_FOUND", "Booking was not found.");
    }

    private record HoldShape(Long tripId, Long pickupTripStopId, Long dropoffTripStopId,
            LinkedHashMap<Long, String> seats, List<Long> segmentIds) {}
}
