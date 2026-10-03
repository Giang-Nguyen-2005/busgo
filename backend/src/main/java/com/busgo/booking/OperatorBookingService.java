package com.busgo.booking;

import static com.busgo.booking.OperatorBookingDtos.*;
import static com.busgo.common.time.BusGoTime.api;

import com.busgo.booking.entity.BookingStatus;
import com.busgo.booking.repository.OperatorBookingQueryRepository;
import com.busgo.common.exception.ResourceNotFoundException;
import com.busgo.common.response.PagedResponse;
import com.busgo.common.security.CurrentUser;
import com.busgo.common.time.BusGoTime;
import com.busgo.operator.OperatorContextService;
import com.busgo.payment.entity.PaymentStatus;
import java.time.*;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperatorBookingService {
    private final OperatorBookingQueryRepository queries;
    private final OperatorContextService context;
    private final CancellationService cancellations;

    public OperatorBookingService(OperatorBookingQueryRepository queries,
            OperatorContextService context, CancellationService cancellations) {
        this.queries = queries;
        this.context = context; this.cancellations=cancellations;
    }

    @Transactional(readOnly = true)
    public PagedResponse<OperatorBookingListItem> list(CurrentUser user, String q, Long tripId,
            BookingStatus status, PaymentStatus paymentStatus, LocalDate date, int page, int size) {
        Long operatorId = context.requireOperatorMember(user).getId();
        BusGoTime.UtcWindow window = date == null ? null : BusGoTime.businessDate(date);
        String search = q == null || q.isBlank() ? null
                : "%" + q.strip().toLowerCase(Locale.ROOT) + "%";
        var result = queries.search(operatorId, search, tripId, status, paymentStatus,
                window == null ? null : window.startInclusive(),
                window == null ? null : window.endExclusive(), page, size);
        var data = result.rows().stream().map(row -> new OperatorBookingListItem(
                row.bookingId(), row.bookingCode(), row.status(), row.paymentStatus(), row.source(), row.paymentMethod(), row.tripId(),
                new RouteSummary(row.routeId(), row.routeName()),
                new Contact(row.contactName(), row.contactPhone(), row.contactEmail()),
                new Stop(row.pickupId(), row.pickupLocationId(), row.pickupName(), api(row.pickupTime())),
                new Stop(row.dropoffId(), row.dropoffLocationId(), row.dropoffName(), api(row.dropoffTime())),
                row.seatCount(), row.totalAmount(), api(row.createdAt()))).toList();
        int pages = result.total() == 0 ? 0 : (int) ((result.total() + size - 1) / size);
        return new PagedResponse<>(data,
                new PagedResponse.Pagination(page, size, result.total(), pages));
    }

    @Transactional(readOnly = true)
    public OperatorBookingDetail detail(CurrentUser user, Long bookingId) {
        Long operatorId = context.requireOperatorMember(user).getId();
        var row = queries.findOwnedDetail(operatorId, bookingId)
                .orElseThrow(OperatorBookingService::notFound);
        var items = queries.findItems(operatorId, bookingId).stream().map(item ->
                new BookingItemSummary(item.id(), item.tripSeatId(), item.seatCode(),
                        item.passengerName(), item.unitPrice(), item.ticketId() == null ? null
                        : new TicketSummary(item.ticketId(), item.ticketCode(),
                                item.ticketPassengerName(), item.ticketSeatCode(), item.paymentId(),
                                api(item.ticketCreatedAt()), item.ticketStatus()))).toList();
        var payments = queries.findPayments(operatorId, bookingId).stream().map(payment ->
                new PaymentSummary(payment.id(), payment.method(), payment.amount(), payment.status(),
                        payment.transactionReference(), api(payment.paidAt()),
                        api(payment.createdAt()), payment.collectedByUserId(), payment.referenceNote())).toList();
        return new OperatorBookingDetail(row.bookingId(), row.bookingCode(), row.status(), row.source(), row.paymentMethod(),
                new TripSummary(row.tripId(), row.tripStatus(), api(row.departureTime()),
                        api(row.estimatedArrivalTime())),
                new RouteSummary(row.routeId(), row.routeName()),
                row.customerId() == null ? null : new CustomerSummary(row.customerId(), row.customerName(), row.customerEmail(),
                        row.customerPhone()),
                new Contact(row.contactName(), row.contactPhone(), row.contactEmail()),
                new Stop(row.pickupId(), row.pickupLocationId(), row.pickupName(), api(row.pickupTime())),
                new Stop(row.dropoffId(), row.dropoffLocationId(), row.dropoffName(), api(row.dropoffTime())),
                items, payments, row.totalAmount(), api(row.createdAt()), api(row.updatedAt()), cancellations.operatorRead(user,bookingId));
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("BOOKING_NOT_FOUND", "Booking was not found.");
    }
}
