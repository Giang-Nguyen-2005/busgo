package com.busgo.trip.operations;

import com.busgo.booking.entity.BookingStatus;
import com.busgo.payment.entity.PaymentStatus;
import com.busgo.trip.entity.InventoryStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class OperatorOccupancyQueryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public OperatorOccupancyQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ManifestRow> manifest(Long operatorId, Long tripId) {
        return jdbc.query("""
                SELECT b.id AS booking_id, b.booking_code, b.status AS booking_status,
                       bi.id AS booking_item_id, bi.trip_seat_id, bi.seat_code,
                       bi.passenger_name, b.contact_name, b.contact_phone, b.contact_email,
                       pickup.id AS pickup_id, pickup.location_id AS pickup_location_id,
                       pickup_location.name AS pickup_name, pickup.planned_departure_time AS pickup_time,
                       dropoff.id AS dropoff_id, dropoff.location_id AS dropoff_location_id,
                       dropoff_location.name AS dropoff_name, dropoff.planned_arrival_time AS dropoff_time,
                       tk.passenger_name AS ticket_passenger_name, tk.ticket_code,
                       COALESCE(p.status, 'PENDING') AS payment_status
                FROM bookings b
                JOIN booking_items bi ON bi.booking_id = b.id
                JOIN trips t ON t.id = b.trip_id
                JOIN operator_routes opr ON opr.id = t.operator_route_id
                JOIN trip_stops pickup ON pickup.id = b.pickup_trip_stop_id
                JOIN locations pickup_location ON pickup_location.id = pickup.location_id
                JOIN trip_stops dropoff ON dropoff.id = b.dropoff_trip_stop_id
                JOIN locations dropoff_location ON dropoff_location.id = dropoff.location_id
                LEFT JOIN tickets tk ON tk.booking_item_id = bi.id
                LEFT JOIN payments p ON p.id = (
                    SELECT MAX(p2.id) FROM payments p2 WHERE p2.booking_id = b.id)
                WHERE b.trip_id = :tripId AND opr.operator_id = :operatorId
                  AND b.status IN ('CONFIRMED', 'COMPLETED')
                ORDER BY pickup.stop_order, dropoff.stop_order, b.id, bi.id
                """, params(operatorId, tripId), (rs, rowNum) -> new ManifestRow(
                        rs.getLong("booking_id"), rs.getString("booking_code"),
                        BookingStatus.valueOf(rs.getString("booking_status")),
                        rs.getLong("booking_item_id"), rs.getLong("trip_seat_id"),
                        rs.getString("seat_code"), rs.getString("passenger_name"),
                        rs.getString("contact_name"), rs.getString("contact_phone"),
                        rs.getString("contact_email"), rs.getLong("pickup_id"),
                        rs.getLong("pickup_location_id"), rs.getString("pickup_name"),
                        rs.getObject("pickup_time", LocalDateTime.class), rs.getLong("dropoff_id"),
                        rs.getLong("dropoff_location_id"), rs.getString("dropoff_name"),
                        rs.getObject("dropoff_time", LocalDateTime.class),
                        rs.getString("ticket_passenger_name"), rs.getString("ticket_code"),
                        PaymentStatus.valueOf(rs.getString("payment_status"))));
    }

    public List<OccupancyRow> occupancy(Long operatorId, Long tripId) {
        return jdbc.query("""
                SELECT seat.id AS trip_seat_id, seat.seat_code, seat.row_no, seat.column_no,
                       seat.floor_no, seat.seat_type, segment.id AS trip_segment_id,
                       segment.segment_order, segment.from_trip_stop_id, segment.to_trip_stop_id,
                       from_location.name AS from_name, to_location.name AS to_name,
                       inventory.status, inventory.hold_expires_at,
                       b.id AS booking_id, b.booking_code, b.status AS booking_status
                FROM trip_seat_segment_inventory inventory
                JOIN trip_seats seat ON seat.id = inventory.trip_seat_id
                JOIN trips t ON t.id = seat.trip_id
                JOIN operator_routes opr ON opr.id = t.operator_route_id
                JOIN trip_segments segment ON segment.id = inventory.trip_segment_id
                JOIN trip_stops from_stop ON from_stop.id = segment.from_trip_stop_id
                JOIN locations from_location ON from_location.id = from_stop.location_id
                JOIN trip_stops to_stop ON to_stop.id = segment.to_trip_stop_id
                JOIN locations to_location ON to_location.id = to_stop.location_id
                LEFT JOIN booking_items bi ON bi.id = inventory.booking_item_id
                LEFT JOIN bookings b ON b.id = bi.booking_id
                WHERE seat.trip_id = :tripId AND segment.trip_id = :tripId
                  AND opr.operator_id = :operatorId
                ORDER BY seat.floor_no, seat.row_no, seat.column_no, seat.id,
                         segment.segment_order, segment.id
                """, params(operatorId, tripId), (rs, rowNum) -> new OccupancyRow(
                        rs.getLong("trip_seat_id"), rs.getString("seat_code"),
                        rs.getInt("row_no"), rs.getInt("column_no"), rs.getInt("floor_no"),
                        com.busgo.fleet.entity.SeatType.valueOf(rs.getString("seat_type")),
                        rs.getLong("trip_segment_id"), rs.getInt("segment_order"),
                        rs.getLong("from_trip_stop_id"), rs.getLong("to_trip_stop_id"),
                        rs.getString("from_name"), rs.getString("to_name"),
                        InventoryStatus.valueOf(rs.getString("status")),
                        rs.getObject("hold_expires_at", LocalDateTime.class),
                        rs.getObject("booking_id", Long.class), rs.getString("booking_code"),
                        rs.getString("booking_status") == null ? null
                                : BookingStatus.valueOf(rs.getString("booking_status"))));
    }

    private static MapSqlParameterSource params(Long operatorId, Long tripId) {
        return new MapSqlParameterSource("operatorId", operatorId).addValue("tripId", tripId);
    }

    public record ManifestRow(Long bookingId, String bookingCode, BookingStatus bookingStatus,
            Long bookingItemId, Long tripSeatId, String seatCode, String passengerName,
            String contactName, String contactPhone, String contactEmail,
            Long pickupId, Long pickupLocationId, String pickupName, LocalDateTime pickupTime,
            Long dropoffId, Long dropoffLocationId, String dropoffName, LocalDateTime dropoffTime,
            String ticketPassengerName, String ticketCode, PaymentStatus paymentStatus) {}

    public record OccupancyRow(Long tripSeatId, String seatCode, int row, int column, int floor,
            com.busgo.fleet.entity.SeatType seatType, Long tripSegmentId, int segmentOrder,
            Long fromTripStopId, Long toTripStopId, String fromName, String toName,
            InventoryStatus status, LocalDateTime holdExpiresAt, Long bookingId,
            String bookingCode, BookingStatus bookingStatus) {}
}
