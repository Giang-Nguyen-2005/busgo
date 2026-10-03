package com.busgo.booking.repository;

import com.busgo.booking.entity.BookingStatus;
import com.busgo.payment.entity.*;
import com.busgo.trip.entity.TripStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.busgo.common.time.JpaJdbcTime;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class OperatorBookingQueryRepository {
    private static final String FROM = """
            FROM bookings b
            JOIN trips t ON t.id = b.trip_id
            JOIN operator_routes opr ON opr.id = t.operator_route_id
            JOIN routes r ON r.id = opr.route_id
            JOIN trip_stops pickup ON pickup.id = b.pickup_trip_stop_id
            JOIN locations pickup_location ON pickup_location.id = pickup.location_id
            JOIN trip_stops dropoff ON dropoff.id = b.dropoff_trip_stop_id
            JOIN locations dropoff_location ON dropoff_location.id = dropoff.location_id
            LEFT JOIN payments p ON p.id = (
                SELECT MAX(p2.id) FROM payments p2 WHERE p2.booking_id = b.id)
            WHERE opr.operator_id = :operatorId
              AND (:tripId IS NULL OR b.trip_id = :tripId)
              AND (:status IS NULL OR b.status = :status)
              AND (:paymentStatus IS NULL OR COALESCE(p.status, 'PENDING') = :paymentStatus)
              AND (:startTime IS NULL OR b.created_at >= :startTime)
              AND (:endTime IS NULL OR b.created_at < :endTime)
              AND (:q IS NULL OR LOWER(b.booking_code) LIKE :q
                   OR LOWER(b.contact_name) LIKE :q OR LOWER(b.contact_phone) LIKE :q
                   OR LOWER(b.contact_email) LIKE :q)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public OperatorBookingQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageRows search(Long operatorId, String q, Long tripId, BookingStatus status,
            PaymentStatus paymentStatus, LocalDateTime startTime, LocalDateTime endTime,
            int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("operatorId", operatorId).addValue("q", q)
                .addValue("tripId", tripId)
                .addValue("status", status == null ? null : status.name())
                .addValue("paymentStatus", paymentStatus == null ? null : paymentStatus.name())
                .addValue("startTime", JpaJdbcTime.parameter(startTime)).addValue("endTime", JpaJdbcTime.parameter(endTime))
                .addValue("limit", size).addValue("offset", Math.multiplyExact(page, size));
        long total = jdbc.queryForObject("SELECT COUNT(*) " + FROM, params, Long.class);
        List<ListRow> rows = jdbc.query("""
                SELECT b.id, b.booking_code, b.status, b.source, b.payment_method,
                       COALESCE(p.status, 'PENDING') AS payment_status,
                       b.trip_id, r.id AS route_id, r.name AS route_name,
                       b.contact_name, b.contact_phone, b.contact_email,
                       pickup.id AS pickup_id, pickup.location_id AS pickup_location_id,
                       pickup_location.name AS pickup_name,
                       pickup.planned_departure_time AS pickup_time,
                       dropoff.id AS dropoff_id, dropoff.location_id AS dropoff_location_id,
                       dropoff_location.name AS dropoff_name,
                       dropoff.planned_arrival_time AS dropoff_time,
                       (SELECT COUNT(*) FROM booking_items bi WHERE bi.booking_id = b.id) AS seat_count,
                       b.total_amount, b.created_at
                """ + FROM + " ORDER BY b.created_at DESC, b.id DESC LIMIT :limit OFFSET :offset",
                params, (rs, rowNum) -> new ListRow(
                        rs.getLong("id"), rs.getString("booking_code"),
                        BookingStatus.valueOf(rs.getString("status")), com.busgo.booking.entity.BookingSource.valueOf(rs.getString("source")), PaymentMethod.valueOf(rs.getString("payment_method")),
                        PaymentStatus.valueOf(rs.getString("payment_status")),
                        rs.getLong("trip_id"), rs.getLong("route_id"),
                        rs.getString("route_name"), rs.getString("contact_name"),
                        rs.getString("contact_phone"), rs.getString("contact_email"),
                        rs.getLong("pickup_id"), rs.getLong("pickup_location_id"),
                        rs.getString("pickup_name"),
                        JpaJdbcTime.read(rs, "pickup_time"),
                        rs.getLong("dropoff_id"), rs.getLong("dropoff_location_id"),
                        rs.getString("dropoff_name"),
                        JpaJdbcTime.read(rs, "dropoff_time"),
                        rs.getInt("seat_count"), rs.getBigDecimal("total_amount"),
                        JpaJdbcTime.read(rs, "created_at")));
        return new PageRows(rows, total);
    }

    public Optional<DetailRow> findOwnedDetail(Long operatorId, Long bookingId) {
        List<DetailRow> rows = jdbc.query("""
                SELECT b.id, b.booking_code, b.status, b.source, b.payment_method, b.total_amount, b.created_at, b.updated_at,
                       b.contact_name, b.contact_phone, b.contact_email,
                       u.id AS customer_id, u.full_name, u.email AS customer_email, u.phone AS customer_phone,
                       t.id AS trip_id, t.status AS trip_status, t.departure_time,
                       t.estimated_arrival_time, r.id AS route_id, r.name AS route_name,
                       pickup.id AS pickup_id, pickup.location_id AS pickup_location_id,
                       pickup_location.name AS pickup_name, pickup.planned_departure_time AS pickup_time,
                       dropoff.id AS dropoff_id, dropoff.location_id AS dropoff_location_id,
                       dropoff_location.name AS dropoff_name, dropoff.planned_arrival_time AS dropoff_time
                FROM bookings b
                LEFT JOIN users u ON u.id = b.customer_id
                JOIN trips t ON t.id = b.trip_id
                JOIN operator_routes opr ON opr.id = t.operator_route_id
                JOIN routes r ON r.id = opr.route_id
                JOIN trip_stops pickup ON pickup.id = b.pickup_trip_stop_id
                JOIN locations pickup_location ON pickup_location.id = pickup.location_id
                JOIN trip_stops dropoff ON dropoff.id = b.dropoff_trip_stop_id
                JOIN locations dropoff_location ON dropoff_location.id = dropoff.location_id
                WHERE b.id = :bookingId AND opr.operator_id = :operatorId
                """, new MapSqlParameterSource("bookingId", bookingId)
                        .addValue("operatorId", operatorId),
                (rs, rowNum) -> new DetailRow(rs.getLong("id"), rs.getString("booking_code"),
                        BookingStatus.valueOf(rs.getString("status")), com.busgo.booking.entity.BookingSource.valueOf(rs.getString("source")), PaymentMethod.valueOf(rs.getString("payment_method")), rs.getBigDecimal("total_amount"),
                        JpaJdbcTime.read(rs, "created_at"),
                        JpaJdbcTime.read(rs, "updated_at"),
                        rs.getString("contact_name"), rs.getString("contact_phone"),
                        rs.getString("contact_email"), rs.getObject("customer_id", Long.class),
                        rs.getString("full_name"), rs.getString("customer_email"),
                        rs.getString("customer_phone"), rs.getLong("trip_id"),
                        TripStatus.valueOf(rs.getString("trip_status")),
                        JpaJdbcTime.read(rs, "departure_time"),
                        JpaJdbcTime.read(rs, "estimated_arrival_time"),
                        rs.getLong("route_id"), rs.getString("route_name"),
                        rs.getLong("pickup_id"), rs.getLong("pickup_location_id"),
                        rs.getString("pickup_name"), JpaJdbcTime.read(rs, "pickup_time"),
                        rs.getLong("dropoff_id"), rs.getLong("dropoff_location_id"),
                        rs.getString("dropoff_name"), JpaJdbcTime.read(rs, "dropoff_time")));
        return rows.stream().findFirst();
    }

    public List<ItemRow> findItems(Long operatorId, Long bookingId) {
        return jdbc.query("""
                SELECT bi.id, bi.trip_seat_id, bi.seat_code, bi.passenger_name, bi.unit_price,
                       tk.id AS ticket_id, tk.ticket_code, tk.passenger_name AS ticket_passenger_name,
                       tk.seat_code AS ticket_seat_code, tk.payment_id, tk.created_at AS ticket_created_at, tk.status AS ticket_status
                FROM booking_items bi
                JOIN bookings b ON b.id = bi.booking_id
                JOIN trips t ON t.id = b.trip_id
                JOIN operator_routes opr ON opr.id = t.operator_route_id
                LEFT JOIN tickets tk ON tk.booking_item_id = bi.id
                WHERE b.id = :bookingId AND opr.operator_id = :operatorId
                ORDER BY bi.id
                """, params(operatorId, bookingId), (rs, rowNum) -> new ItemRow(
                        rs.getLong("id"), rs.getLong("trip_seat_id"), rs.getString("seat_code"),
                        rs.getString("passenger_name"), rs.getBigDecimal("unit_price"),
                        rs.getObject("ticket_id", Long.class), rs.getString("ticket_code"),
                        rs.getString("ticket_passenger_name"), rs.getString("ticket_seat_code"),
                        rs.getObject("payment_id", Long.class),
                        JpaJdbcTime.read(rs, "ticket_created_at"), rs.getString("ticket_status")));
    }

    public List<PaymentRow> findPayments(Long operatorId, Long bookingId) {
        return jdbc.query("""
                SELECT p.id, p.method, p.amount, p.status, p.transaction_reference,
                       p.paid_at, p.created_at, p.collected_by_user_id, p.reference_note
                FROM payments p
                JOIN bookings b ON b.id = p.booking_id
                JOIN trips t ON t.id = b.trip_id
                JOIN operator_routes opr ON opr.id = t.operator_route_id
                WHERE b.id = :bookingId AND opr.operator_id = :operatorId
                ORDER BY p.created_at, p.id
                """, params(operatorId, bookingId), (rs, rowNum) -> new PaymentRow(
                        rs.getLong("id"), PaymentMethod.valueOf(rs.getString("method")),
                        rs.getBigDecimal("amount"), PaymentStatus.valueOf(rs.getString("status")),
                        rs.getString("transaction_reference"),
                        JpaJdbcTime.read(rs, "paid_at"),
                        JpaJdbcTime.read(rs, "created_at"), rs.getObject("collected_by_user_id", Long.class), rs.getString("reference_note")));
    }

    private static MapSqlParameterSource params(Long operatorId, Long bookingId) {
        return new MapSqlParameterSource("operatorId", operatorId)
                .addValue("bookingId", bookingId);
    }

    public record PageRows(List<ListRow> rows, long total) {}
    public record ListRow(Long bookingId, String bookingCode, BookingStatus status, com.busgo.booking.entity.BookingSource source, PaymentMethod paymentMethod,
            PaymentStatus paymentStatus, Long tripId, Long routeId, String routeName,
            String contactName, String contactPhone, String contactEmail,
            Long pickupId, Long pickupLocationId, String pickupName, LocalDateTime pickupTime,
            Long dropoffId, Long dropoffLocationId, String dropoffName, LocalDateTime dropoffTime,
            int seatCount, BigDecimal totalAmount, LocalDateTime createdAt) {}
    public record DetailRow(Long bookingId, String bookingCode, BookingStatus status, com.busgo.booking.entity.BookingSource source, PaymentMethod paymentMethod,
            BigDecimal totalAmount, LocalDateTime createdAt, LocalDateTime updatedAt,
            String contactName, String contactPhone, String contactEmail,
            Long customerId, String customerName, String customerEmail, String customerPhone,
            Long tripId, TripStatus tripStatus, LocalDateTime departureTime,
            LocalDateTime estimatedArrivalTime, Long routeId, String routeName,
            Long pickupId, Long pickupLocationId, String pickupName, LocalDateTime pickupTime,
            Long dropoffId, Long dropoffLocationId, String dropoffName, LocalDateTime dropoffTime) {}
    public record ItemRow(Long id, Long tripSeatId, String seatCode, String passengerName,
            BigDecimal unitPrice, Long ticketId, String ticketCode, String ticketPassengerName,
            String ticketSeatCode, Long paymentId, LocalDateTime ticketCreatedAt, String ticketStatus) {}
    public record PaymentRow(Long id, PaymentMethod method, BigDecimal amount,
            PaymentStatus status, String transactionReference, LocalDateTime paidAt,
            LocalDateTime createdAt, Long collectedByUserId, String referenceNote) {}
}
