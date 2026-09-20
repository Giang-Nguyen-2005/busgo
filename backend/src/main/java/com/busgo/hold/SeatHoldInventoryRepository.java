package com.busgo.hold;

import com.busgo.trip.entity.InventoryStatus;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class SeatHoldInventoryRepository {
    private static final String LOCK_REQUIRED = """
            SELECT inventory.id, inventory.trip_seat_id, inventory.trip_segment_id,
                   inventory.status, inventory.hold_expires_at
            FROM trip_seat_segment_inventory inventory
            WHERE inventory.trip_seat_id IN (:seatIds)
              AND inventory.trip_segment_id IN (:segmentIds)
            ORDER BY inventory.trip_seat_id ASC, inventory.trip_segment_id ASC
            FOR UPDATE
            """;

    private static final String FIND_HOLD = """
            SELECT inventory.status, inventory.hold_expires_at,
                   seat.trip_id, seat.id AS trip_seat_id, seat.seat_code,
                   segment.id AS trip_segment_id, segment.segment_order,
                   segment.from_trip_stop_id, segment.to_trip_stop_id
            FROM trip_seat_segment_inventory inventory
            JOIN trip_seats seat ON seat.id = inventory.trip_seat_id
            JOIN trip_segments segment ON segment.id = inventory.trip_segment_id
            WHERE inventory.hold_token = :holdToken
              AND inventory.held_by_user_id = :userId
            ORDER BY seat.id ASC, segment.segment_order ASC
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public SeatHoldInventoryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<LockedInventory> lockRequired(List<Long> seatIds, List<Long> segmentIds) {
        var parameters = new MapSqlParameterSource()
                .addValue("seatIds", seatIds)
                .addValue("segmentIds", segmentIds);
        return jdbc.query(LOCK_REQUIRED, parameters, (rs, rowNumber) -> new LockedInventory(
                rs.getLong("id"), rs.getLong("trip_seat_id"), rs.getLong("trip_segment_id"),
                InventoryStatus.valueOf(rs.getString("status")),
                rs.getObject("hold_expires_at", LocalDateTime.class)));
    }

    public int markHeld(List<Long> inventoryIds, String token, Long userId,
            LocalDateTime expiresAt, LocalDateTime now) {
        String sql = """
                UPDATE trip_seat_segment_inventory
                SET status = 'HELD', hold_token = :holdToken, held_by_user_id = :userId,
                    hold_expires_at = :expiresAt, updated_at = :now, version = version + 1
                WHERE id IN (:inventoryIds)
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("holdToken", token).addValue("userId", userId)
                .addValue("expiresAt", expiresAt).addValue("now", now)
                .addValue("inventoryIds", inventoryIds));
    }

    public List<HoldInventory> findOwnedHold(String token, Long userId) {
        return jdbc.query(FIND_HOLD, new MapSqlParameterSource()
                        .addValue("holdToken", token).addValue("userId", userId),
                (rs, rowNumber) -> new HoldInventory(
                        InventoryStatus.valueOf(rs.getString("status")),
                        rs.getObject("hold_expires_at", LocalDateTime.class),
                        rs.getLong("trip_id"), rs.getLong("trip_seat_id"),
                        rs.getString("seat_code"), rs.getLong("trip_segment_id"),
                        rs.getInt("segment_order"), rs.getLong("from_trip_stop_id"),
                        rs.getLong("to_trip_stop_id")));
    }

    public int releaseOwned(String token, Long userId, LocalDateTime now) {
        String sql = """
                UPDATE trip_seat_segment_inventory
                SET status = 'AVAILABLE', hold_token = NULL, held_by_user_id = NULL,
                    hold_expires_at = NULL, updated_at = :now, version = version + 1
                WHERE hold_token = :holdToken
                  AND held_by_user_id = :userId
                  AND status = 'HELD'
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("holdToken", token).addValue("userId", userId).addValue("now", now));
    }

    public int releaseExpired(LocalDateTime now) {
        String sql = """
                UPDATE trip_seat_segment_inventory
                SET status = 'AVAILABLE', hold_token = NULL, held_by_user_id = NULL,
                    hold_expires_at = NULL, updated_at = :now, version = version + 1
                WHERE status = 'HELD' AND hold_expires_at <= :now
                """;
        return jdbc.update(sql, new MapSqlParameterSource("now", now));
    }

    public record LockedInventory(Long id, Long tripSeatId, Long tripSegmentId,
            InventoryStatus status, LocalDateTime holdExpiresAt) {}

    public record HoldInventory(InventoryStatus status, LocalDateTime holdExpiresAt,
            Long tripId, Long tripSeatId, String seatCode, Long tripSegmentId,
            Integer segmentOrder, Long fromTripStopId, Long toTripStopId) {}
}
