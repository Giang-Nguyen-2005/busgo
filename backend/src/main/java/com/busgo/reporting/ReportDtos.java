package com.busgo.reporting;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Map;
import com.busgo.common.response.PagedResponse.Pagination;

public final class ReportDtos {
    private ReportDtos() {}
    public record Metadata(LocalDate fromDate, LocalDate toDate, String timezone,
            OffsetDateTime asOf, String dateBasis, Long routeId, Long tripId,
            String bookingSource, String paymentMethod) {}
    public record Money(BigDecimal grossMockCollections, BigDecimal mockRefunds,
            BigDecimal netMockCollections, long paidPaymentCount, long refundedPaymentCount) {}
    public record DailyCollections(LocalDate date, Money money) {}
    public record Collections(Money totals, Map<String, Money> byPaymentMethod,
            List<DailyCollections> trend) {}
    public record Bookings(long bookingsCreated, Map<String, Long> byStatus,
            Map<String, Long> bySource, long cancellations, long paymentTimeouts,
            long currentUnpaidCount, long ticketsIssued, long validTickets, long voidTickets) {}
    public record Cancellations(long totalCancellations, Map<String, Long> byReason,
            long refundedCancellations, long unpaidCancellations, BigDecimal amountRefunded) {}
    public record Attendance(long eligibleResolvedTickets, long boardedTickets,
            long noShowTickets, long checkedInNotBoarded, long unresolvedAttendance,
            long ticketlessNoShows, Double boardingRate, Double noShowRate) {}
    public record Load(long expectedCells, long actualCells, long missingCells,
            long sellableCells, long reservedCells, long paidCells, long heldCells,
            boolean complete, Double reservedSegmentLoad, Double paidSegmentLoad) {}
    public record Operations(long trips, long boardingTrips, long runningTrips,
            long upcomingWithoutDriver, long openPickups, long incompleteTrips) {}
    public record Summary(Metadata metadata, Collections collections, Bookings bookings,
            Cancellations cancellations, Attendance attendance, Load load, Operations operations) {}
    public record TripPerformance(long tripId, long routeId, String route, OffsetDateTime plannedDeparture,
            String status, String bus, long bookings, long validTickets, Attendance attendance,
            Money money, Load load, Long wholeTripAvailableSeats) {}
    public record RoutePerformance(long routeId, String route, long tripCount, long operatedTripCount,
            long bookings, long validTickets, Attendance attendance, Money money, Load load) {}
    public record Table<T>(Metadata metadata, List<T> data, Pagination pagination) {}
}
