package com.busgo.payment;

import static com.busgo.common.time.BusGoTime.api;
import static com.busgo.common.time.BusGoTime.utc;
import static com.busgo.payment.PaymentTicketDtos.*;

import com.busgo.booking.entity.*;
import com.busgo.booking.repository.*;
import com.busgo.common.exception.BusinessException;
import com.busgo.common.exception.ResourceNotFoundException;
import com.busgo.common.security.CurrentUser;
import com.busgo.payment.BookingPaymentInventoryRepository.BookedRow;
import com.busgo.payment.entity.*;
import com.busgo.payment.repository.PaymentRepository;
import com.busgo.ticket.entity.Ticket;
import com.busgo.ticket.repository.TicketRepository;
import com.busgo.trip.entity.*;
import com.busgo.trip.search.TripSegmentResolver;
import com.busgo.user.entity.User;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentTicketService {
    private final BookingRepository bookings;
    private final BookingItemRepository bookingItems;
    private final PaymentRepository payments;
    private final TicketRepository tickets;
    private final BookingStatusHistoryRepository histories;
    private final BookingPaymentInventoryRepository inventory;
    private final TripSegmentResolver segments;
    private final EntityManager entityManager;
    private final Clock clock;

    public PaymentTicketService(BookingRepository bookings,
            BookingItemRepository bookingItems, PaymentRepository payments,
            TicketRepository tickets, BookingStatusHistoryRepository histories,
            BookingPaymentInventoryRepository inventory, TripSegmentResolver segments,
            EntityManager entityManager, Clock clock) {
        this.bookings = bookings;
        this.bookingItems = bookingItems;
        this.payments = payments;
        this.tickets = tickets;
        this.histories = histories;
        this.inventory = inventory;
        this.segments = segments;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional
    public PaymentConfirmation confirm(CurrentUser currentUser, Long bookingId) {
        LocalDateTime now = utc(clock.instant());
        Booking booking = bookings.lockOwnedById(bookingId, currentUser.id())
                .orElseThrow(PaymentTicketService::bookingNotFound);
        List<BookingItem> items = bookingItems.findDetailedByBookingId(booking.getId());
        validateInventory(booking, items);

        Optional<Payment> paid = payments.findByBookingIdAndStatus(
                booking.getId(), PaymentStatus.PAID);
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            Payment existing = paid.orElseThrow(PaymentTicketService::invalidPayment);
            List<Ticket> existingTickets = tickets.findDetailedByBookingId(booking.getId());
            if (!completeTicketSet(booking, items, existing, existingTickets)) {
                throw invalidPayment();
            }
            return confirmation(booking, existing);
        }
        if (booking.getStatus() != BookingStatus.PENDING) throw notPayable();
        if (paid.isPresent()) throw invalidPayment();

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setMethod(PaymentMethod.MOCK_QR);
        payment.setAmount(booking.getTotalAmount());
        payment.setStatus(PaymentStatus.PAID);
        payment.setTransactionReference(newTransactionReference());
        payment.setPaidAt(now);
        payment = payments.saveAndFlush(payment);

        booking.setStatus(BookingStatus.CONFIRMED);
        BookingStatusHistory history = new BookingStatusHistory();
        history.setBooking(booking);
        history.setFromStatus(BookingStatus.PENDING);
        history.setToStatus(BookingStatus.CONFIRMED);
        history.setChangedBy(entityManager.getReference(User.class, currentUser.id()));
        history.setNote("Mock QR payment confirmed");
        history.setChangedAt(now);
        histories.save(history);

        List<Ticket> created = new ArrayList<>();
        for (BookingItem item : items) {
            Ticket ticket = new Ticket();
            ticket.setTicketCode(newTicketCode());
            ticket.setBooking(booking);
            ticket.setBookingItem(item);
            ticket.setPayment(payment);
            ticket.setPassengerName(passengerName(booking, item));
            ticket.setSeatCode(item.getSeatCode());
            created.add(ticket);
        }
        tickets.saveAllAndFlush(created);
        return confirmation(booking, payment);
    }

    @Transactional(readOnly = true)
    public TicketBundle ticket(CurrentUser currentUser, Long bookingId) {
        Booking booking = bookings.findOwnedById(bookingId, currentUser.id())
                .orElseThrow(PaymentTicketService::bookingNotFound);
        if (booking.getStatus() != BookingStatus.CONFIRMED) throw ticketNotAvailable();
        Payment payment = payments.findByBookingIdAndStatus(bookingId, PaymentStatus.PAID)
                .orElseThrow(PaymentTicketService::ticketNotAvailable);
        List<BookingItem> items = bookingItems.findDetailedByBookingId(bookingId);
        List<Ticket> ticketRows = tickets.findDetailedByBookingId(bookingId);
        if (!completeTicketSet(booking, items, payment, ticketRows)) {
            throw ticketNotAvailable();
        }
        return bundle(booking, payment, ticketRows);
    }

    private void validateInventory(Booking booking, List<BookingItem> items) {
        if (items.isEmpty()) throw inconsistentInventory();
        List<Long> expectedSegments = segments.resolve(booking.getTrip().getId(),
                        booking.getPickupTripStop(), booking.getDropoffTripStop()).stream()
                .map(TripSegment::getId).toList();
        if (expectedSegments.isEmpty()) throw inconsistentInventory();

        List<BookedRow> rows = inventory.lockByBookingItems(
                items.stream().map(BookingItem::getId).toList());
        if (rows.size() != Math.multiplyExact(items.size(), expectedSegments.size())) {
            throw inconsistentInventory();
        }
        Map<Long, List<BookedRow>> rowsByItem = rows.stream()
                .collect(Collectors.groupingBy(BookedRow::bookingItemId));
        for (BookingItem item : items) {
            List<BookedRow> itemRows = rowsByItem.getOrDefault(item.getId(), List.of());
            if (itemRows.size() != expectedSegments.size()
                    || itemRows.stream().anyMatch(row -> row.status() != InventoryStatus.BOOKED
                            || row.holdToken() != null || row.heldByUserId() != null
                            || row.holdExpiresAt() != null
                            || !item.getTripSeat().getId().equals(row.tripSeatId()))
                    || !itemRows.stream().map(BookedRow::tripSegmentId).toList()
                            .equals(expectedSegments)) {
                throw inconsistentInventory();
            }
        }
    }

    private boolean completeTicketSet(Booking booking, List<BookingItem> items,
            Payment payment, List<Ticket> ticketRows) {
        if (ticketRows.size() != items.size()) return false;
        Map<Long, BookingItem> itemById = items.stream()
                .collect(Collectors.toMap(BookingItem::getId, Function.identity()));
        Set<Long> seen = new HashSet<>();
        for (Ticket ticket : ticketRows) {
            BookingItem item = itemById.get(ticket.getBookingItem().getId());
            if (item == null || !seen.add(item.getId())
                    || !booking.getId().equals(ticket.getBooking().getId())
                    || !payment.getId().equals(ticket.getPayment().getId())
                    || !item.getSeatCode().equals(ticket.getSeatCode())
                    || ticket.getPassengerName() == null || ticket.getPassengerName().isBlank()) {
                return false;
            }
        }
        return seen.size() == items.size();
    }

    private PaymentConfirmation confirmation(Booking booking, Payment payment) {
        return new PaymentConfirmation(payment.getId(), booking.getId(),
                booking.getBookingCode(), payment.getMethod(), payment.getAmount(),
                payment.getStatus(), booking.getStatus(), payment.getTransactionReference(),
                api(payment.getPaidAt()));
    }

    private TicketBundle bundle(Booking booking, Payment payment, List<Ticket> rows) {
        Trip trip = booking.getTrip();
        var operator = trip.getOperatorRoute().getOperator();
        var route = trip.getOperatorRoute().getRoute();
        var pickup = booking.getPickupTripStop();
        var dropoff = booking.getDropoffTripStop();
        List<TicketItem> result = rows.stream().map(ticket -> new TicketItem(
                ticket.getId(), ticket.getTicketCode(), ticket.getPassengerName(),
                ticket.getSeatCode(), ticket.getTicketCode())).toList();
        return new TicketBundle(booking.getId(), booking.getBookingCode(), booking.getStatus(),
                payment.getStatus(), payment.getMethod(), payment.getAmount(), trip.getId(),
                new NamedSummary(operator.getId(), operator.getName()),
                new NamedSummary(route.getId(), route.getName()),
                new TicketStop(pickup.getId(), pickup.getLocation().getId(),
                        pickup.getLocation().getName(), api(pickup.getPlannedDepartureTime())),
                new TicketStop(dropoff.getId(), dropoff.getLocation().getId(),
                        dropoff.getLocation().getName(), api(dropoff.getPlannedArrivalTime())),
                api(pickup.getPlannedDepartureTime()), api(dropoff.getPlannedArrivalTime()), result);
    }

    private static String passengerName(Booking booking, BookingItem item) {
        return item.getPassengerName() == null || item.getPassengerName().isBlank()
                ? booking.getContactName() : item.getPassengerName();
    }

    private static String newTransactionReference() {
        return "MOCK-" + UUID.randomUUID().toString().replace("-", "")
                .toUpperCase(Locale.ROOT);
    }

    private static String newTicketCode() {
        return "TKT-" + UUID.randomUUID().toString().replace("-", "")
                .toUpperCase(Locale.ROOT);
    }

    private static ResourceNotFoundException bookingNotFound() {
        return new ResourceNotFoundException("BOOKING_NOT_FOUND", "Booking was not found.");
    }

    private static BusinessException notPayable() {
        return new BusinessException("BOOKING_NOT_PAYABLE",
                "Booking is not payable in its current state.", HttpStatus.CONFLICT, null);
    }

    private static BusinessException invalidPayment() {
        return new BusinessException("PAYMENT_ALREADY_INVALID",
                "The booking payment state is inconsistent.", HttpStatus.CONFLICT, null);
    }

    private static BusinessException inconsistentInventory() {
        return new BusinessException("BOOKING_INVENTORY_INCONSISTENT",
                "Booked seat inventory is inconsistent with the booking.",
                HttpStatus.CONFLICT, null);
    }

    private static BusinessException ticketNotAvailable() {
        return new BusinessException("TICKET_NOT_AVAILABLE",
                "Ticket is not available for this booking.", HttpStatus.CONFLICT, null);
    }
}
