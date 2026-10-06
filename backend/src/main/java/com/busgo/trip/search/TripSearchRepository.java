package com.busgo.trip.search;

import static com.busgo.common.time.BusGoTime.api;
import com.busgo.trip.entity.TripStatus;
import com.busgo.trip.search.TripSearchDtos.*;
import com.busgo.marketplace.MarketplaceRepository;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class TripSearchRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public TripSearchRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String BASE = """
        SELECT t.id trip_id,op.id operator_id,op.name operator_name,r.id route_id,r.name route_name,
        bt.id bus_type_id,bt.name bus_type_name,b.image_url,
        pickup.id pickup_stop_id,pickup.location_id pickup_location_id,pl.name pickup_name,pickup.planned_departure_time,
        dropoff.id dropoff_stop_id,dropoff.location_id dropoff_location_id,dl.name dropoff_name,dropoff.planned_arrival_time,
        fare.price,t.status,t.delay_minutes,ratings.average_rating,COALESCE(ratings.review_count,0) review_count,
        ROW_NUMBER() OVER(PARTITION BY t.id ORDER BY fare.price,pickup.stop_order,dropoff.stop_order) choice,
        %s available_seats
        FROM trips t JOIN operator_routes opr ON opr.id=t.operator_route_id
        JOIN transport_operators op ON op.id=opr.operator_id JOIN routes r ON r.id=opr.route_id
        JOIN buses b ON b.id=t.bus_id JOIN bus_types bt ON bt.id=b.bus_type_id
        JOIN trip_stops pickup ON pickup.trip_id=t.id JOIN trip_stops dropoff ON dropoff.trip_id=t.id
        JOIN locations pl ON pl.id=pickup.location_id JOIN locations dl ON dl.id=dropoff.location_id
        JOIN operator_route_fares fare ON fare.operator_route_id=opr.id
        AND fare.from_route_stop_id=pickup.source_route_stop_id AND fare.to_route_stop_id=dropoff.source_route_stop_id
        %s
        WHERE (:pickupId IS NULL OR pickup.location_id=:pickupId)
        AND (:dropoffId IS NULL OR dropoff.location_id=:dropoffId)
        AND pickup.allow_pickup=true AND dropoff.allow_dropoff=true
        AND pickup.status='ACTIVE' AND dropoff.status='ACTIVE' AND pickup.stop_order < dropoff.stop_order
        AND pickup.planned_departure_time IS NOT NULL AND dropoff.planned_arrival_time IS NOT NULL
        AND fare.status='ACTIVE' AND opr.status='ACTIVE' AND op.status='ACTIVE' AND fare.price>0
        AND t.status='SCHEDULED' AND pickup.planned_departure_time>=:dateStart
        AND pickup.planned_departure_time<:dateEnd AND pickup.planned_departure_time>:now
        AND (:operatorId IS NULL OR op.id=:operatorId) AND (:busTypeId IS NULL OR bt.id=:busTypeId)
        AND (:minPrice IS NULL OR fare.price>=:minPrice) AND (:maxPrice IS NULL OR fare.price<=:maxPrice)
        AND (:departureFrom IS NULL OR pickup.planned_departure_time>=:departureFrom)
        AND (:departureTo IS NULL OR pickup.planned_departure_time<=:departureTo)
        AND (:minRating IS NULL OR ratings.average_rating>=:minRating)
        """.formatted(SeatAvailabilityQueryRepository.availableSeatCount("t.id","pickup.stop_order","dropoff.stop_order"),MarketplaceRepository.RATING_JOIN);
    public Page<SearchResult> search(Criteria c,Pageable page) {
        var p=new MapSqlParameterSource().addValue("pickupId",c.pickupLocationId()).addValue("dropoffId",c.dropoffLocationId())
            .addValue("dateStart",sqlTime(c.dateStart())).addValue("dateEnd",sqlTime(c.dateEnd())).addValue("now",sqlTime(c.nowUtc()))
            .addValue("operatorId",c.operatorId()).addValue("busTypeId",c.busTypeId()).addValue("minPrice",c.minPrice())
            .addValue("maxPrice",c.maxPrice()).addValue("departureFrom",sqlTime(c.departureFrom())).addValue("departureTo",sqlTime(c.departureTo()))
            .addValue("minRating",c.minRating()).addValue("minSeats",c.minSeats()).addValue("limit",page.getPageSize()).addValue("offset",page.getOffset());
        String filtered=" FROM ("+BASE+") candidate WHERE choice=1 AND available_seats>=:minSeats";
        long total=jdbc.queryForObject("SELECT COUNT(*)"+filtered,p,Long.class);
        String order=switch(c.sort()) {
            case PRICE_ASC -> "price ASC,planned_departure_time ASC";
            case PRICE_DESC -> "price DESC,planned_departure_time ASC";
            case DEPARTURE_DESC -> "planned_departure_time DESC";
            case RATING_DESC, RECOMMENDED -> "average_rating DESC,review_count DESC,planned_departure_time ASC";
            case DEPARTURE_ASC -> "planned_departure_time ASC";
        };
        var rows=jdbc.query("SELECT *"+filtered+" ORDER BY "+order+",trip_id ASC LIMIT :limit OFFSET :offset",p,(rs,n)-> {
            var departure=rs.getTimestamp("planned_departure_time").toLocalDateTime();
            var arrival=rs.getTimestamp("planned_arrival_time").toLocalDateTime();
            return new SearchResult(rs.getLong("trip_id"),new OperatorSummary(rs.getLong("operator_id"),rs.getString("operator_name"),
                rs.getObject("average_rating")==null?null:rs.getDouble("average_rating"),rs.getLong("review_count")),
                new RouteSummary(rs.getLong("route_id"),rs.getString("route_name")),new BusTypeSummary(rs.getLong("bus_type_id"),rs.getString("bus_type_name")),rs.getString("image_url"),
                new PickupSummary(rs.getLong("pickup_stop_id"),rs.getLong("pickup_location_id"),rs.getString("pickup_name"),api(departure)),
                new DropoffSummary(rs.getLong("dropoff_stop_id"),rs.getLong("dropoff_location_id"),rs.getString("dropoff_name"),api(arrival)),
                ChronoUnit.MINUTES.between(departure,arrival),rs.getBigDecimal("price"),rs.getLong("available_seats"),TripStatus.valueOf(rs.getString("status")),rs.getInt("delay_minutes"),
                com.busgo.trip.operations.LiveTripState.label(TripStatus.valueOf(rs.getString("status")),rs.getInt("delay_minutes")),
                api(departure.plusMinutes(rs.getInt("delay_minutes"))),api(arrival.plusMinutes(rs.getInt("delay_minutes"))));
        });
        return new PageImpl<>(rows,page,total);
    }
    private static java.sql.Timestamp sqlTime(LocalDateTime value) { return value == null ? null : java.sql.Timestamp.valueOf(value); }
    public record Criteria(Long pickupLocationId,Long dropoffLocationId,LocalDateTime dateStart,LocalDateTime dateEnd,
        LocalDateTime nowUtc,Long operatorId,Long busTypeId,BigDecimal minPrice,BigDecimal maxPrice,
        LocalDateTime departureFrom,LocalDateTime departureTo,SearchSort sort,Double minRating,int minSeats) {}
}
