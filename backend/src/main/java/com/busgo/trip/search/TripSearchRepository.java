package com.busgo.trip.search;

import static com.busgo.common.time.BusGoTime.api;

import com.busgo.common.entity.ActiveStatus;
import com.busgo.trip.entity.TripStatus;
import com.busgo.trip.search.TripSearchDtos.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class TripSearchRepository {
    private static final String CANDIDATES = """
            select t.id, op.id, op.name, r.id, r.name, bt.id, bt.name,
                   pickup.id, pickup.location.id, pickup.location.name,
                   pickup.plannedDepartureTime,
                   dropoff.id, dropoff.location.id, dropoff.location.name,
                   dropoff.plannedArrivalTime, fare.price, t.status,
                   pickup.stopOrder, dropoff.stopOrder
            from Trip t
            join t.operatorRoute operatorRoute
            join operatorRoute.operator op
            join operatorRoute.route r
            join t.bus b
            join b.busType bt
            join TripStop pickup on pickup.trip = t
            join TripStop dropoff on dropoff.trip = t
            join OperatorRouteFare fare on fare.operatorRoute = operatorRoute
                and fare.fromRouteStop = pickup.sourceRouteStop
                and fare.toRouteStop = dropoff.sourceRouteStop
            where pickup.location.id = :pickupLocationId
              and pickup.allowPickup = true
              and pickup.status = :active
              and pickup.plannedDepartureTime is not null
              and dropoff.location.id = :dropoffLocationId
              and dropoff.allowDropoff = true
              and dropoff.status = :active
              and dropoff.plannedArrivalTime is not null
              and pickup.stopOrder < dropoff.stopOrder
              and fare.status = :active
              and fare.price > 0
              and t.status = :scheduled
              and pickup.plannedDepartureTime >= :dateStart
              and pickup.plannedDepartureTime < :dateEnd
              and pickup.plannedDepartureTime > :nowUtc
              and (:operatorId is null or op.id = :operatorId)
              and (:busTypeId is null or bt.id = :busTypeId)
              and (:minPrice is null or fare.price >= :minPrice)
              and (:maxPrice is null or fare.price <= :maxPrice)
              and (:departureFrom is null or pickup.plannedDepartureTime >= :departureFrom)
              and (:departureTo is null or pickup.plannedDepartureTime <= :departureTo)
            """;

    private static final String AVAILABILITY = """
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

    private final EntityManager entityManager;
    private final NamedParameterJdbcTemplate jdbc;

    public TripSearchRepository(EntityManager entityManager, NamedParameterJdbcTemplate jdbc) {
        this.entityManager = entityManager;
        this.jdbc = jdbc;
    }

    public Page<SearchResult> search(Criteria criteria, Pageable pageable) {
        var query = entityManager.createQuery(CANDIDATES, Object[].class);
        query.setParameter("pickupLocationId", criteria.pickupLocationId());
        query.setParameter("dropoffLocationId", criteria.dropoffLocationId());
        query.setParameter("active", ActiveStatus.ACTIVE);
        query.setParameter("scheduled", TripStatus.SCHEDULED);
        query.setParameter("dateStart", criteria.dateStart());
        query.setParameter("dateEnd", criteria.dateEnd());
        query.setParameter("nowUtc", criteria.nowUtc());
        query.setParameter("operatorId", criteria.operatorId());
        query.setParameter("busTypeId", criteria.busTypeId());
        query.setParameter("minPrice", criteria.minPrice());
        query.setParameter("maxPrice", criteria.maxPrice());
        query.setParameter("departureFrom", criteria.departureFrom());
        query.setParameter("departureTo", criteria.departureTo());

        Map<Long, RawCandidate> unique = new LinkedHashMap<>();
        for (Object[] row : query.getResultList()) {
            RawCandidate candidate = raw(row);
            unique.merge(candidate.tripId(), candidate,
                    (left, right) -> left.price().compareTo(right.price()) <= 0 ? left : right);
        }
        if (unique.isEmpty()) return new PageImpl<>(List.of(), pageable, 0);

        Map<Long, Long> available = availability(unique.keySet(), criteria);
        List<SearchResult> matched = unique.values().stream()
                .filter(candidate -> available.getOrDefault(candidate.tripId(), 0L) > 0)
                .map(candidate -> response(candidate, available.get(candidate.tripId())))
                .sorted(comparator(criteria.sort()))
                .toList();
        int from = pageable.getOffset() >= matched.size() ? matched.size() : (int) pageable.getOffset();
        int to = Math.min(from + pageable.getPageSize(), matched.size());
        return new PageImpl<>(matched.subList(from, to), pageable, matched.size());
    }

    private Map<Long, Long> availability(Set<Long> tripIds, Criteria criteria) {
        var parameters = new MapSqlParameterSource()
                .addValue("tripIds", tripIds)
                .addValue("pickupLocationId", criteria.pickupLocationId())
                .addValue("dropoffLocationId", criteria.dropoffLocationId());
        Map<Long, Long> result = new HashMap<>();
        jdbc.query(AVAILABILITY, parameters, (rs, rowNumber) -> Map.entry(
                rs.getLong("trip_id"), rs.getLong("available_seats")))
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private static RawCandidate raw(Object[] row) {
        return new RawCandidate((Long) row[0], (Long) row[1], (String) row[2], (Long) row[3],
                (String) row[4], (Long) row[5], (String) row[6], (Long) row[7], (Long) row[8],
                (String) row[9], (LocalDateTime) row[10], (Long) row[11], (Long) row[12],
                (String) row[13], (LocalDateTime) row[14], (BigDecimal) row[15],
                (TripStatus) row[16], (Integer) row[17], (Integer) row[18]);
    }

    private static SearchResult response(RawCandidate candidate, long available) {
        return new SearchResult(candidate.tripId(),
                new OperatorSummary(candidate.operatorId(), candidate.operatorName()),
                new RouteSummary(candidate.routeId(), candidate.routeName()),
                new BusTypeSummary(candidate.busTypeId(), candidate.busTypeName()),
                new PickupSummary(candidate.pickupStopId(), candidate.pickupLocationId(),
                        candidate.pickupName(), api(candidate.pickupDeparture())),
                new DropoffSummary(candidate.dropoffStopId(), candidate.dropoffLocationId(),
                        candidate.dropoffName(), api(candidate.dropoffArrival())),
                ChronoUnit.MINUTES.between(candidate.pickupDeparture(), candidate.dropoffArrival()),
                candidate.price(), available, candidate.status());
    }

    private static Comparator<SearchResult> comparator(SearchSort sort) {
        Comparator<SearchResult> byDeparture = Comparator.comparing(
                result -> result.pickup().departureTime());
        Comparator<SearchResult> comparator = switch (sort) {
            case PRICE_ASC -> Comparator.comparing(SearchResult::price).thenComparing(byDeparture);
            case PRICE_DESC -> Comparator.comparing(SearchResult::price).reversed().thenComparing(byDeparture);
            case DEPARTURE_DESC -> byDeparture.reversed();
            case DEPARTURE_ASC -> byDeparture;
        };
        return comparator.thenComparing(SearchResult::tripId);
    }

    public record Criteria(Long pickupLocationId, Long dropoffLocationId,
            LocalDateTime dateStart, LocalDateTime dateEnd, LocalDateTime nowUtc,
            Long operatorId, Long busTypeId, BigDecimal minPrice, BigDecimal maxPrice,
            LocalDateTime departureFrom, LocalDateTime departureTo, SearchSort sort) {}

    private record RawCandidate(Long tripId, Long operatorId, String operatorName,
            Long routeId, String routeName, Long busTypeId, String busTypeName,
            Long pickupStopId, Long pickupLocationId, String pickupName, LocalDateTime pickupDeparture,
            Long dropoffStopId, Long dropoffLocationId, String dropoffName, LocalDateTime dropoffArrival,
            BigDecimal price, TripStatus status, Integer pickupOrder, Integer dropoffOrder) {}
}
