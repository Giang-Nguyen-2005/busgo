package com.busgo.trip.search;

import com.busgo.fleet.entity.SeatType;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class SeatAvailabilityQueryRepository {
    private static final String AVAILABLE_COUNTS = """
            SELECT seat.trip_id, COUNT(*) AS available_seats
            FROM trip_seats seat
            JOIN trip_stops pickup
              ON pickup.trip_id = seat.trip_id AND pickup.location_id = :pickupLocationId
            JOIN trip_stops dropoff
              ON dropoff.trip_id = seat.trip_id AND dropoff.location_id = :dropoffLocationId
            WHERE seat.trip_id IN (:tripIds)
              AND (SELECT COUNT(*) FROM trip_segments required_segment
                   WHERE required_segment.trip_id = seat.trip_id
                     AND required_segment.segment_order >= pickup.stop_order
                     AND required_segment.segment_order < dropoff.stop_order)
                    = dropoff.stop_order - pickup.stop_order
              AND NOT EXISTS (
                  SELECT 1
                  FROM trip_segments segment
                  LEFT JOIN trip_seat_segment_inventory inventory
                    ON inventory.trip_segment_id = segment.id
                   AND inventory.trip_seat_id = seat.id
                  WHERE segment.trip_id = seat.trip_id
                    AND segment.segment_order >= pickup.stop_order
                    AND segment.segment_order < dropoff.stop_order
                    AND (inventory.id IS NULL OR inventory.status <> 'AVAILABLE')
              )
            GROUP BY seat.trip_id
            """;

    private static final String SEAT_MAP = """
            SELECT seat.id, seat.seat_code, seat.row_no, seat.column_no, seat.floor_no,
                   seat.seat_type,
                   CASE WHEN
                     (SELECT COUNT(*) FROM trip_segments required_segment
                      WHERE required_segment.trip_id = :tripId
                        AND required_segment.segment_order >= :pickupOrder
                        AND required_segment.segment_order < :dropoffOrder) = :requiredSegmentCount
                     AND NOT EXISTS (
                       SELECT 1
                       FROM trip_segments segment
                       LEFT JOIN trip_seat_segment_inventory inventory
                         ON inventory.trip_segment_id = segment.id
                        AND inventory.trip_seat_id = seat.id
                       WHERE segment.trip_id = :tripId
                         AND segment.segment_order >= :pickupOrder
                         AND segment.segment_order < :dropoffOrder
                         AND (inventory.id IS NULL OR inventory.status <> 'AVAILABLE')
                     ) THEN TRUE ELSE FALSE END AS available
            FROM trip_seats seat
            WHERE seat.trip_id = :tripId
            ORDER BY seat.floor_no, seat.row_no, seat.column_no, seat.seat_code
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public SeatAvailabilityQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<Long, Long> availableCounts(Set<Long> tripIds, Long pickupLocationId,
            Long dropoffLocationId) {
        if (tripIds.isEmpty()) return Map.of();
        var parameters = new MapSqlParameterSource()
                .addValue("tripIds", tripIds)
                .addValue("pickupLocationId", pickupLocationId)
                .addValue("dropoffLocationId", dropoffLocationId);
        Map<Long, Long> result = new HashMap<>();
        jdbc.query(AVAILABLE_COUNTS, parameters, (rs, rowNumber) -> Map.entry(
                rs.getLong("trip_id"), rs.getLong("available_seats")))
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    public List<SeatAvailabilityRow> seatMap(Long tripId, int pickupOrder, int dropoffOrder,
            int requiredSegmentCount) {
        var parameters = new MapSqlParameterSource()
                .addValue("tripId", tripId)
                .addValue("pickupOrder", pickupOrder)
                .addValue("dropoffOrder", dropoffOrder)
                .addValue("requiredSegmentCount", requiredSegmentCount);
        return jdbc.query(SEAT_MAP, parameters, (rs, rowNumber) -> new SeatAvailabilityRow(
                rs.getLong("id"), rs.getString("seat_code"), rs.getInt("row_no"),
                rs.getInt("column_no"), rs.getInt("floor_no"),
                SeatType.valueOf(rs.getString("seat_type")), rs.getBoolean("available")));
    }

    public record SeatAvailabilityRow(Long tripSeatId, String seatCode, Integer row,
            Integer column, Integer floor, SeatType seatType, boolean available) {}
}
