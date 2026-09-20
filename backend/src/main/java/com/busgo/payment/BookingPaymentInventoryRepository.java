package com.busgo.payment;

import com.busgo.trip.entity.InventoryStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BookingPaymentInventoryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public BookingPaymentInventoryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<BookedRow> lockByBookingItems(List<Long> bookingItemIds) {
        if (bookingItemIds.isEmpty()) return List.of();
        return jdbc.query("""
                SELECT inventory.id, inventory.trip_seat_id, inventory.trip_segment_id,
                       inventory.status, inventory.hold_token, inventory.held_by_user_id,
                       inventory.hold_expires_at, inventory.booking_item_id
                FROM trip_seat_segment_inventory inventory
                JOIN trip_segments segment ON segment.id = inventory.trip_segment_id
                WHERE inventory.booking_item_id IN (:bookingItemIds)
                ORDER BY inventory.trip_seat_id, segment.segment_order, inventory.id
                FOR UPDATE
                """, new MapSqlParameterSource("bookingItemIds", bookingItemIds),
                (rs, rowNumber) -> new BookedRow(rs.getLong("id"),
                        rs.getLong("trip_seat_id"), rs.getLong("trip_segment_id"),
                        InventoryStatus.valueOf(rs.getString("status")),
                        rs.getString("hold_token"),
                        rs.getObject("held_by_user_id", Long.class),
                        rs.getObject("hold_expires_at", LocalDateTime.class),
                        rs.getObject("booking_item_id", Long.class)));
    }

    public record BookedRow(Long id, Long tripSeatId, Long tripSegmentId,
            InventoryStatus status, String holdToken, Long heldByUserId,
            LocalDateTime holdExpiresAt, Long bookingItemId) {}
}
