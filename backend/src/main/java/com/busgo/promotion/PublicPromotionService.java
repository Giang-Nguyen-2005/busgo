package com.busgo.promotion;

import static com.busgo.common.time.BusGoTime.*;
import static com.busgo.common.time.JpaJdbcTime.*;
import static com.busgo.promotion.PublicPromotionDtos.*;

import com.busgo.common.response.PagedResponse;
import com.busgo.trip.search.CustomerJourneyResolver;
import java.time.Clock;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class PublicPromotionService {
    // Shared by public list, trip list and the search projection. Counts only global capacity,
    // with the same redeemed/unexpired-reserved meaning as M24B; never reserves or quotes.
    public static final String DISCOVERABLE = """
        v.customer_visible=TRUE AND v.state='ACTIVE' AND v.starts_at<=:now AND v.ends_at>:now
        AND (v.total_limit IS NULL OR (SELECT COUNT(*) FROM voucher_usages pu WHERE pu.voucher_id=v.id
          AND (pu.state='REDEEMED' OR (pu.state='RESERVED' AND pu.expires_at>:now))) < v.total_limit)
        """;
    public static final String SEARCH_EXISTS = "EXISTS(SELECT 1 FROM operator_vouchers v WHERE "
            + DISCOVERABLE + " AND v.operator_id=op.id AND (v.operator_route_id IS NULL OR v.operator_route_id=opr.id))";
    private static final String FROM = """
        FROM operator_vouchers v JOIN transport_operators op ON op.id=v.operator_id
        LEFT JOIN operator_routes opr ON opr.id=v.operator_route_id AND opr.operator_id=v.operator_id
        LEFT JOIN routes r ON r.id=opr.route_id
        WHERE op.status='ACTIVE' AND
        """ + DISCOVERABLE + """
        AND ((v.operator_route_id IS NOT NULL AND opr.status='ACTIVE') OR
             (v.operator_route_id IS NULL AND EXISTS(SELECT 1 FROM operator_routes available_route
               WHERE available_route.operator_id=op.id AND available_route.status='ACTIVE')))
        AND (:operatorId IS NULL OR v.operator_id=:operatorId)
        AND (:operatorRouteId IS NULL OR v.operator_route_id IS NULL OR v.operator_route_id=:operatorRouteId)
        """;
    private final NamedParameterJdbcTemplate jdbc;
    private final CustomerJourneyResolver journeys;
    private final Clock clock;
    public PublicPromotionService(NamedParameterJdbcTemplate jdbc, CustomerJourneyResolver journeys, Clock clock) {
        this.jdbc=jdbc; this.journeys=journeys; this.clock=clock;
    }
    public PagedResponse<Promotion> list(Long operatorId,int page,int size) {
        return query(operatorId,null,page,size,Eligibility.REQUIRES_CONTEXT);
    }
    public PagedResponse<Promotion> trip(long tripId,long pickup,long dropoff,int page,int size) {
        var journey=journeys.resolve(tripId,pickup,dropoff);
        var route=journey.trip().getOperatorRoute();
        return query(route.getOperator().getId(),route.getId(),page,size,Eligibility.CONDITIONAL);
    }
    private PagedResponse<Promotion> query(Long operatorId,Long routeId,int page,int size,Eligibility eligibility) {
        if(page<0 || page>100000 || size<1 || size>20) throw new IllegalArgumentException("Invalid promotion page");
        var params=new MapSqlParameterSource().addValue("now",parameter(utc(clock.instant())))
                .addValue("operatorId",operatorId).addValue("operatorRouteId",routeId)
                .addValue("limit",size).addValue("offset",(long)page*size);
        long count=jdbc.queryForObject("SELECT COUNT(*) "+FROM,params,Long.class);
        var rows=jdbc.query("""
            SELECT v.id,v.code,v.name,v.description,v.discount_type,v.discount_value,v.max_discount,
              v.minimum_spend,v.starts_at,v.ends_at,v.customer_limit,op.id operator_id,op.name operator_name,
              r.id route_id,r.name route_name,r.origin_location_id,r.destination_location_id
            """+FROM+" ORDER BY v.id DESC LIMIT :limit OFFSET :offset",params,(rs,n) -> new Promotion(
                rs.getLong("id"),new Operator(rs.getLong("operator_id"),rs.getString("operator_name")),
                rs.getObject("route_id")==null ? null : new Route(rs.getLong("route_id"),rs.getString("route_name"),
                        rs.getLong("origin_location_id"),rs.getLong("destination_location_id")),
                rs.getString("code"),rs.getString("name"),rs.getString("description"),rs.getString("discount_type"),
                rs.getBigDecimal("discount_value"),rs.getBigDecimal("max_discount"),rs.getBigDecimal("minimum_spend"),
                api(read(rs,"starts_at")),api(read(rs,"ends_at")),rs.getObject("customer_limit",Integer.class),eligibility));
        return new PagedResponse<>(rows,new PagedResponse.Pagination(page,size,count,(int)((count+size-1)/size)));
    }
}
