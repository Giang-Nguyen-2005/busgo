package com.busgo.trip.operations;

import com.busgo.booking.entity.BookingStatus;
import com.busgo.payment.entity.PaymentStatus;
import com.busgo.trip.entity.*;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;

public final class OperatorTripOperationsDtos {
    private OperatorTripOperationsDtos() {}

    public record Contact(String name, String phone, String email) {}
    public record Stop(Long tripStopId, Long locationId, String name, OffsetDateTime time) {}
    public record PassengerManifestRow(Long bookingId, String bookingCode,
            BookingStatus bookingStatus, Long bookingItemId, Long tripSeatId, String seatCode,
            Stop pickup, Stop dropoff, String passengerName, String ticketPassengerName,
            Contact contact, PaymentStatus paymentStatus, String ticketCode) {}
    public record PassengerManifest(Long tripId, TripStatus tripStatus,
            List<PassengerManifestRow> passengers) {}

    public record InventoryCounts(long available, long held, long booked, long blocked) {}
    public record OccupancySegment(Long tripSegmentId, int segmentOrder,
            Long fromTripStopId, Long toTripStopId, String fromName, String toName,
            InventoryCounts counts) {}
    public record SeatSegmentState(Long tripSegmentId, int segmentOrder,
            InventoryStatus status, boolean missing, OffsetDateTime holdExpiresAt,
            Long bookingId, String bookingCode, BookingStatus bookingStatus) {}
    public record OccupancySeat(Long tripSeatId, String seatCode, int row, int column,
            int floor, com.busgo.fleet.entity.SeatType seatType,
            List<SeatSegmentState> segments) {}
    public record TripOccupancy(Long tripId, TripStatus tripStatus, long seatCount,
            long segmentCount, long wholeTripAvailableSeatCount,
            boolean complete, long expectedInventoryCellCount,
            long actualInventoryCellCount, long missingInventoryCellCount,
            List<OccupancySegment> segments, List<OccupancySeat> seats) {}

    public record UpdateTripStatusRequest(@NotNull TripStatus status) {}
    public record TripStatusResponse(Long tripId, TripStatus status) {}
}
