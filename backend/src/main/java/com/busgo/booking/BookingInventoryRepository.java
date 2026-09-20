package com.busgo.booking;

import com.busgo.trip.entity.InventoryStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BookingInventoryRepository {
    private static final String LOCK_OWNED_HOLD = """
            SELECT inventory.id, inventory.trip_seat_id, inventory.trip_segment_id,
                   inventory.status, inventory.hold_token, inventory.held_by_user_id,
                   inventory.hold_expires_at, inventory.booking_item_id,
                   seat.trip_id, seat.seat_code,
                   segment.segment_order, segment.from_trip_stop_id, segment.to_trip_stop_id
            FROM trip_seat_segment_inventory inventory
            JOIN trip_seats seat ON seat.id = inventory.trip_seat_id
            JOIN trip_segments segment ON segment.id = inventory.trip_segment_id
            WHERE inventory.hold_token = :holdToken
              AND inventory.held_by_user_id = :userId
            ORDER BY inventory.trip_seat_id ASC, segment.segment_order ASC, inventory.id ASC
            FOR UPDATE
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public BookingInventoryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<LockedHoldRow> lockOwnedHold(String holdToken, Long userId) {
        return jdbc.query(LOCK_OWNED_HOLD, new MapSqlParameterSource()
                        .addValue("holdToken", holdToken).addValue("userId", userId),
                (rs, rowNumber) -> new LockedHoldRow(
                        rs.getLong("id"), rs.getLong("trip_seat_id"),
                        rs.getLong("trip_segment_id"),
                        InventoryStatus.valueOf(rs.getString("status")),
                        rs.getString("hold_token"), rs.getLong("held_by_user_id"),
                        rs.getObject("hold_expires_at", LocalDateTime.class),
                        rs.getObject("booking_item_id", Long.class), rs.getLong("trip_id"),
                        rs.getString("seat_code"), rs.getInt("segment_order"),
                        rs.getLong("from_trip_stop_id"), rs.getLong("to_trip_stop_id")));
    }

    public int convertToBooked(List<Long> inventoryIds, Long bookingItemId,
            String holdToken, Long userId, LocalDateTime now) {
        return jdbc.update("""
                UPDATE trip_seat_segment_inventory
                SET status = 'BOOKED', hold_token = NULL, held_by_user_id = NULL,
                    hold_expires_at = NULL, booking_item_id = :bookingItemId,
                    updated_at = :now, version = version + 1
                WHERE id IN (:inventoryIds)
                  AND status = 'HELD'
                  AND hold_token = :holdToken
                  AND held_by_user_id = :userId
                  AND hold_expires_at > :now
                  AND booking_item_id IS NULL
                """, new MapSqlParameterSource().addValue("inventoryIds", inventoryIds)
                .addValue("bookingItemId", bookingItemId).addValue("holdToken", holdToken)
                .addValue("userId", userId).addValue("now", now));
    }

    public record LockedHoldRow(Long id, Long tripSeatId, Long tripSegmentId,
            InventoryStatus status, String holdToken, Long heldByUserId,
            LocalDateTime holdExpiresAt, Long bookingItemId, Long tripId, String seatCode,
            Integer segmentOrder, Long fromTripStopId, Long toTripStopId) {}
}
