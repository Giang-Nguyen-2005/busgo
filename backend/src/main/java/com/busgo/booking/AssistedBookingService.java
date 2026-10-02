package com.busgo.booking;

import com.busgo.booking.entity.*;
import com.busgo.booking.repository.BookingRepository;
import com.busgo.common.exception.*;
import com.busgo.common.security.CurrentUser;
import com.busgo.hold.*;
import com.busgo.operator.OperatorContextService;
import com.busgo.payment.PaymentTicketService;
import com.busgo.payment.PaymentTicketDtos.PaymentConfirmation;
import com.busgo.payment.entity.PaymentMethod;
import com.busgo.trip.repository.TripRepository;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.busgo.booking.AssistedBookingDtos.*;
import static com.busgo.common.time.BusGoTime.api;

@Service
public class AssistedBookingService {
    private final OperatorContextService context;
    private final TripRepository trips;
    private final SeatHoldService holds;
    private final BookingService bookingService;
    private final BookingRepository bookings;
    private final PaymentTicketService payments;
    private final SecureRandom random = new SecureRandom();

    public AssistedBookingService(OperatorContextService context, TripRepository trips,
            SeatHoldService holds, BookingService bookingService, BookingRepository bookings,
            PaymentTicketService payments) {
        this.context = context; this.trips = trips; this.holds = holds;
        this.bookingService = bookingService; this.bookings = bookings; this.payments = payments;
    }

    @Transactional
    public BookingDtos.BookingResponse create(CurrentUser actor, CreateRequest request) {
        Long operatorId = context.requireAdminOperator(actor).getId();
        var trip = trips.findById(request.tripId()).orElseThrow(AssistedBookingService::notFound);
        if (!trip.getOperatorRoute().getOperator().getId().equals(operatorId)) throw notFound();
        requirePhoneMethod(request.paymentMethod());
        // One outer transaction: no visible hold or orphan hold on any failure.
        var hold = holds.create(actor, new SeatHoldDtos.CreateSeatHoldRequest(request.tripId(),
                request.pickupLocationId(), request.dropoffLocationId(), request.tripSeatIds()));
        return bookingService.createFromHold(actor, new BookingDtos.CreateBookingRequest(hold.holdToken(),
                request.contactName(), request.contactPhone(), request.contactEmail()),
                BookingSource.PHONE, request.paymentMethod());
    }

    @Transactional
    public PaymentConfirmation record(CurrentUser actor, Long id, RecordPayment request) {
        Booking b = owned(actor, id);
        requirePhoneMethod(request.method());
        if (b.getSource() != BookingSource.PHONE) throw invalid();
        return payments.confirmAssisted(id, b.getTrip().getId(), actor.id(), request.method(),
                request.referenceNote(), null);
    }

    @Transactional
    public PaymentLink issueLink(CurrentUser actor, Long id) {
        Booking b = owned(actor, id);
        if (b.getSource() != BookingSource.PHONE || b.getPaymentMethod() != PaymentMethod.QR_TRANSFER)
            throw invalid();
        // Match commerce lock ordering before changing the token. Reissuing revokes older links.
        trips.lockById(b.getTrip().getId()).orElseThrow(AssistedBookingService::notFound);
        b = bookings.lockById(id).orElseThrow(AssistedBookingService::notFound);
        byte[] secret = new byte[32]; random.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        b.setPaymentTokenHash(hash(token));
        return new PaymentLink("/pay/" + token);
    }

    @Transactional(readOnly = true)
    public PublicPayment publicContext(String token) { return publicView(linkBooking(token)); }

    @Transactional
    public PublicPayment publicConfirm(String token) {
        Booking b = linkBooking(token);
        payments.confirmAssisted(b.getId(), b.getTrip().getId(), null, PaymentMethod.QR_TRANSFER,
                null, hash(token));
        return publicView(b);
    }

    private Booking owned(CurrentUser actor, Long id) {
        Long operatorId = context.requireAdminOperator(actor).getId();
        Booking b = bookings.findById(id).orElseThrow(AssistedBookingService::notFound);
        if (!b.getTrip().getOperatorRoute().getOperator().getId().equals(operatorId)) throw notFound();
        return b;
    }

    private Booking linkBooking(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw linkNotFound();
        Booking b = bookings.findByPaymentTokenHash(hash(token)).orElseThrow(AssistedBookingService::linkNotFound);
        if (b.getSource() != BookingSource.PHONE || b.getPaymentMethod() != PaymentMethod.QR_TRANSFER)
            throw linkNotFound();
        return b;
    }

    private PublicPayment publicView(Booking b) {
        var pickup = b.getPickupTripStop(); var dropoff = b.getDropoffTripStop();
        return new PublicPayment(b.getBookingCode(), b.getTrip().getOperatorRoute().getOperator().getName(),
                b.getTrip().getOperatorRoute().getRoute().getName(),
                new PublicStop(pickup.getLocation().getName(), api(pickup.getPlannedDepartureTime())),
                new PublicStop(dropoff.getLocation().getName(), api(dropoff.getPlannedArrivalTime())),
                b.getItems().stream().map(BookingItem::getSeatCode).toList(), b.getTotalAmount(),
                b.getPaymentMethod(), b.getStatus(), true);
    }

    static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.US_ASCII))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static void requirePhoneMethod(PaymentMethod method) {
        if (method != PaymentMethod.PAY_ON_BOARD && method != PaymentMethod.QR_TRANSFER) throw invalid();
    }
    private static BusinessException invalid() { return new BusinessException("BOOKING_NOT_PAYABLE",
            "This booking does not support the requested payment method.", HttpStatus.CONFLICT, null); }
    private static ResourceNotFoundException notFound() { return new ResourceNotFoundException("BOOKING_NOT_FOUND", "Booking or trip was not found."); }
    private static ResourceNotFoundException linkNotFound() { return new ResourceNotFoundException("PAYMENT_LINK_NOT_FOUND", "Payment link was not found."); }
}
