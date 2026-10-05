package com.busgo.reporting;

import static com.busgo.reporting.ReportDtos.*;
import com.busgo.reporting.ReportDtos.Collections;
import com.busgo.common.time.*;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

/** Each aggregate has a single grain. EXISTS filters never multiply money or bookings. */
@Repository
public class ReportRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public ReportRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String OWNED = """
        FROM trips t JOIN operator_routes opr ON opr.id=t.operator_route_id
        JOIN routes rt ON rt.id=opr.route_id
        WHERE opr.operator_id=:operator AND (:route IS NULL OR rt.id=:route)
          AND (:trip IS NULL OR t.id=:trip)
        """;
    private static final String BOOKING_FILTER = """
        AND (:source IS NULL OR b.source=:source)
        AND (:method IS NULL OR b.payment_method=:method)
        """;
    private static final String SCOPED = """
        WITH owned AS (SELECT t.id, rt.id route_id, rt.name route
        """ + OWNED + """
        ), cohort AS (SELECT b.* FROM bookings b JOIN owned o ON o.id=b.trip_id WHERE 1=1
        """ + BOOKING_FILTER + ") ";
    private static final String TRAVEL = """
        WITH selected AS (SELECT t.*, rt.id route_id, rt.name route
        """ + OWNED + """
        AND t.departure_time>=:start AND t.departure_time<:end),
        cohort AS (SELECT b.* FROM bookings b JOIN selected t ON t.id=b.trip_id WHERE 1=1
        """ + BOOKING_FILTER + ") ";

    public Collections collections(ReportFilter f, long operator) {
        var p=f.parameters(operator);
        StringBuilder buckets=new StringBuilder("CASE ");
        List<LocalDate> dates=f.fromDate().datesUntil(f.toDate().plusDays(1)).toList();
        for(int i=0;i<dates.size();i++) {
            p.addValue("d"+i,JpaJdbcTime.parameter(BusGoTime.businessDate(dates.get(i)).startInclusive()));
            p.addValue("e"+i,JpaJdbcTime.parameter(BusGoTime.businessDate(dates.get(i)).endExclusive()));
            buckets.append("WHEN event_at>=:d").append(i).append(" AND event_at<:e").append(i)
                    .append(" THEN '").append(dates.get(i)).append("' ");
        }
        buckets.append("END");
        String sql=SCOPED+"""
            , events AS (
              SELECT p.method,p.paid_at event_at,p.amount gross,0 refund,1 paid_count,0 refund_count
              FROM payments p JOIN cohort b ON b.id=p.booking_id
              WHERE p.status IN ('PAID','REFUNDED') AND p.paid_at>=:start AND p.paid_at<:end
                AND (:method IS NULL OR p.method=:method)
              UNION ALL
              SELECT p.method,r.refunded_at,0,r.amount,0,1 FROM refunds r
              JOIN payments p ON p.id=r.payment_id JOIN cohort b ON b.id=p.booking_id
              WHERE r.refunded_at>=:start AND r.refunded_at<:end
                AND (:method IS NULL OR p.method=:method)
            ) SELECT method,
            """+buckets+" bucket, SUM(gross) gross,SUM(refund) refund,SUM(paid_count) paid_count,SUM(refund_count) refund_count FROM events GROUP BY method,bucket";
        var methods=new LinkedHashMap<String,Money>();
        for(String m:List.of("MOCK_ONLINE","QR_TRANSFER","PAY_ON_BOARD")) methods.put(m,zeroMoney());
        var daily=new LinkedHashMap<LocalDate,Money>(); dates.forEach(d->daily.put(d,zeroMoney()));
        jdbc.query(sql,p,rs->{
            Money money=money(rs); String method=rs.getString("method");
            methods.put(method,add(methods.getOrDefault(method,zeroMoney()),money));
            LocalDate date=LocalDate.parse(rs.getString("bucket")); daily.put(date,add(daily.get(date),money));
        });
        Money total=methods.values().stream().reduce(zeroMoney(),ReportRepository::add);
        return new Collections(total,methods,daily.entrySet().stream().map(e->new DailyCollections(e.getKey(),e.getValue())).toList());
    }

    public Bookings bookings(ReportFilter f,long operator) {
        var p=f.parameters(operator); var statuses=counts("PENDING","CONFIRMED","CANCELLED","COMPLETED"); var sources=counts("WEB","PHONE");
        long[] n=new long[3];
        jdbc.query(SCOPED+"""
            SELECT b.status,b.source,COUNT(*) n,
              SUM(b.cancellation_reason='PAYMENT_TIMEOUT') timeouts,
              SUM(b.status='PENDING' AND NOT EXISTS(SELECT 1 FROM payments p WHERE p.booking_id=b.id AND p.status IN ('PAID','REFUNDED'))) unpaid
            FROM cohort b WHERE b.created_at>=:start AND b.created_at<:end GROUP BY b.status,b.source
            """,p,rs->{long count=rs.getLong("n");n[0]+=count;n[1]+=rs.getLong("timeouts");n[2]+=rs.getLong("unpaid");statuses.merge(rs.getString("status"),count,Long::sum);sources.merge(rs.getString("source"),count,Long::sum);});
        long[] tickets=jdbc.queryForObject(SCOPED+"""
            SELECT COUNT(*) issued,COALESCE(SUM(tk.status='VALID'),0) valid,COALESCE(SUM(tk.status='VOID'),0) voided
            FROM tickets tk JOIN cohort b ON b.id=tk.booking_id WHERE tk.created_at>=:start AND tk.created_at<:end
            """,p,(rs,i)->new long[]{rs.getLong("issued"),rs.getLong("valid"),rs.getLong("voided")});
        return new Bookings(n[0],statuses,sources,statuses.get("CANCELLED"),n[1],n[2],tickets[0],tickets[1],tickets[2]);
    }
    public Cancellations cancellations(ReportFilter f,long operator) {
        var reasons=counts("CUSTOMER_CANCELLED","OPERATOR_CANCELLED","PAYMENT_TIMEOUT");long[] n=new long[3];BigDecimal[] amount={BigDecimal.ZERO};
        jdbc.query(SCOPED+"""
            SELECT COALESCE(b.cancellation_reason,'UNRECORDED') reason, COUNT(*) n,
              SUM(EXISTS(SELECT 1 FROM payments p JOIN refunds r ON r.payment_id=p.id WHERE p.booking_id=b.id)) refunded,
              SUM(NOT EXISTS(SELECT 1 FROM payments p WHERE p.booking_id=b.id AND p.status IN ('PAID','REFUNDED'))) unpaid,
              SUM(COALESCE((SELECT SUM(r.amount) FROM payments p JOIN refunds r ON r.payment_id=p.id WHERE p.booking_id=b.id),0)) amount
            FROM cohort b WHERE b.status='CANCELLED' AND b.cancelled_at>=:start AND b.cancelled_at<:end GROUP BY b.cancellation_reason
            """,f.parameters(operator),rs->{long count=rs.getLong("n");n[0]+=count;n[1]+=rs.getLong("refunded");n[2]+=rs.getLong("unpaid");amount[0]=amount[0].add(rs.getBigDecimal("amount"));reasons.merge(rs.getString("reason"),count,Long::sum);});
        return new Cancellations(n[0],reasons,n[1],n[2],amount[0]);
    }
    private static final String ELIGIBLE = """
        b.status<>'CANCELLED' AND ((tk.status='VALID' AND b.status IN ('CONFIRMED','COMPLETED')
        AND EXISTS(SELECT 1 FROM payments p WHERE p.id=tk.payment_id AND p.booking_id=b.id AND p.status='PAID'))
        OR (tk.id IS NULL AND b.status='PENDING'))
        """;
    private static final String ATTENDANCE_COLUMNS = """
        COALESCE(SUM(tk.id IS NOT NULL AND a.status='BOARDED'),0) boarded,
        COALESCE(SUM(tk.id IS NOT NULL AND a.status='NO_SHOW'),0) no_show,
        COALESCE(SUM(tk.id IS NOT NULL AND a.status='CHECKED_IN'),0) checked,
        COALESCE(SUM(a.id IS NULL OR a.status='EXPECTED'),0) unresolved,
        COALESCE(SUM(tk.id IS NULL AND a.status='NO_SHOW'),0) ticketless
        """;
    public Attendance attendance(ReportFilter f,long operator) {
        return jdbc.queryForObject(TRAVEL+"SELECT "+ATTENDANCE_COLUMNS+"""
            FROM cohort b JOIN booking_items bi ON bi.booking_id=b.id
            LEFT JOIN tickets tk ON tk.replaced=FALSE AND tk.booking_item_id=bi.id AND tk.booking_id=b.id
            LEFT JOIN ticket_boarding a ON a.booking_item_id=bi.id AND (a.ticket_id=tk.id OR (a.ticket_id IS NULL AND tk.id IS NULL))
            WHERE
            """+ELIGIBLE,f.parameters(operator),(rs,i)->attendance(rs));
    }

    // The expected matrix is seat snapshots × segment snapshots, with stop topology checked too.
    // LEFT JOIN preserves absent cells. Booking filters affect numerators, never capacity.
    private static final String LOAD_CTE = """
        , topology AS (SELECT t.id,
            (SELECT COUNT(*) FROM trip_seats s WHERE s.trip_id=t.id) seat_count,
            (SELECT COUNT(*) FROM trip_segments g WHERE g.trip_id=t.id) segment_count,
            (SELECT COUNT(*) FROM trip_stops z WHERE z.trip_id=t.id) stop_count,
            (SELECT COUNT(*) FROM trip_segments g JOIN trip_stops a ON a.id=g.from_trip_stop_id
              JOIN trip_stops z ON z.id=g.to_trip_stop_id WHERE g.trip_id=t.id AND a.trip_id=t.id AND z.trip_id=t.id
              AND z.stop_order=a.stop_order+1 AND g.segment_order=a.stop_order) valid_segments
          FROM selected t), cells AS (
          SELECT t.id trip_id,s.id seat_id,v.id inventory_id,v.status,
            CASE WHEN b.id IS NOT NULL THEN 1 ELSE 0 END matching,
            CASE WHEN b.status IN ('CONFIRMED','COMPLETED') AND EXISTS(SELECT 1 FROM payments p WHERE p.booking_id=b.id AND p.status='PAID') THEN 1 ELSE 0 END paid
          FROM selected t JOIN trip_seats s ON s.trip_id=t.id JOIN trip_segments g ON g.trip_id=t.id
          LEFT JOIN trip_seat_segment_inventory v ON v.trip_seat_id=s.id AND v.trip_segment_id=g.id
          LEFT JOIN booking_items bi ON bi.id=v.booking_item_id AND bi.trip_seat_id=s.id
          LEFT JOIN cohort b ON b.id=bi.booking_id AND b.trip_id=t.id
        ), seat_load AS (SELECT trip_id,seat_id,COUNT(*) cells,COUNT(inventory_id) actual,
              SUM(status='AVAILABLE') available FROM cells GROUP BY trip_id,seat_id),
        inventory_load AS (SELECT trip_id,COUNT(*) expected,COUNT(inventory_id) actual,
              COALESCE(SUM(status='BLOCKED'),0) blocked,
              COALESCE(SUM(status='BOOKED' AND matching=1),0) reserved,
              COALESCE(SUM(status='BOOKED' AND paid=1),0) paid,
              COALESCE(SUM(status='HELD'),0) held FROM cells GROUP BY trip_id),
        loads AS (SELECT t.id trip_id,COALESCE(v.expected,0) expected,COALESCE(v.actual,0) actual,
              COALESCE(v.expected-v.blocked,0) sellable,COALESCE(v.reserved,0) reserved,
              COALESCE(v.paid,0) paid,COALESCE(v.held,0) held,
              CASE WHEN t.seat_count>0 AND t.segment_count>0 AND t.stop_count=t.segment_count+1
                AND t.valid_segments=t.segment_count AND v.expected=v.actual THEN 1 ELSE 0 END complete,
              (SELECT COUNT(*) FROM seat_load s WHERE s.trip_id=t.id AND s.actual=t.segment_count AND s.available=t.segment_count) whole_available
            FROM topology t LEFT JOIN inventory_load v ON v.trip_id=t.id)
        """;
    public Load load(ReportFilter f,long operator) {
        // Exclude only from load: the shared travel cohort must retain cancelled history.
        return jdbc.queryForObject(TRAVEL+LOAD_CTE+"""
          SELECT COALESCE(SUM(expected),0) expected,COALESCE(SUM(actual),0) actual,
            COALESCE(SUM(sellable),0) sellable,COALESCE(SUM(reserved),0) reserved,
            COALESCE(SUM(paid),0) paid,COALESCE(SUM(held),0) held,
            COALESCE(MIN(complete),1) complete FROM loads l JOIN selected t ON t.id=l.trip_id
            WHERE t.status<>'CANCELLED'
          """,f.parameters(operator),(rs,i)->load(rs));
    }
    public Operations operations(ReportFilter f,long operator) {
        var p=f.parameters(operator).addValue("now",JpaJdbcTime.parameter(BusGoTime.utc(Instant.now())));
        return jdbc.queryForObject(TRAVEL+LOAD_CTE+"""
          SELECT COUNT(*) trips,COALESCE(SUM(t.status='BOARDING'),0) boarding,
            COALESCE(SUM(t.status='DEPARTED'),0) running,COALESCE(SUM(l.complete=0),0) incomplete,
            COALESCE(SUM(t.status IN ('SCHEDULED','BOARDING') AND t.departure_time>=:now AND NOT EXISTS(
              SELECT 1 FROM trip_crew_assignments c JOIN operator_employees e ON e.id=c.employee_id
              WHERE c.trip_id=t.id AND c.duty='DRIVER' AND c.released_at IS NULL AND e.status='ACTIVE' AND e.operator_id=:operator)),0) missing_driver,
            COALESCE(SUM((SELECT COUNT(*) FROM trip_stops z WHERE z.trip_id=t.id AND z.allow_pickup=TRUE AND z.status='ACTIVE'
              AND t.status IN ('SCHEDULED','BOARDING','DEPARTED') AND NOT EXISTS(SELECT 1 FROM trip_stop_operations o WHERE o.trip_id=t.id AND o.stop_id=z.id))),0) pickups
          FROM selected t JOIN loads l ON l.trip_id=t.id
          """,p,(rs,i)->new Operations(rs.getLong("trips"),rs.getLong("boarding"),rs.getLong("running"),rs.getLong("missing_driver"),rs.getLong("pickups"),rs.getLong("incomplete")));
    }

    // Travel performance attributes lifetime transactions to trips departing in the window.
    // Every domain is reduced to one row per trip BEFORE joining.
    private static final String PERFORMANCE = TRAVEL+LOAD_CTE+"""
        , booking_totals AS (SELECT trip_id,COUNT(*) bookings FROM cohort GROUP BY trip_id),
        ticket_totals AS (SELECT b.trip_id,COUNT(*) valid_tickets FROM tickets tk JOIN cohort b ON b.id=tk.booking_id WHERE tk.status='VALID' GROUP BY b.trip_id),
        payment_totals AS (SELECT b.trip_id,SUM(p.amount) gross,COUNT(*) paid_count FROM payments p JOIN cohort b ON b.id=p.booking_id
          WHERE p.status IN ('PAID','REFUNDED') AND p.paid_at IS NOT NULL AND (:method IS NULL OR p.method=:method) GROUP BY b.trip_id),
        refund_totals AS (SELECT b.trip_id,SUM(r.amount) refund,COUNT(*) refund_count FROM refunds r JOIN payments p ON p.id=r.payment_id
          JOIN cohort b ON b.id=p.booking_id WHERE (:method IS NULL OR p.method=:method) GROUP BY b.trip_id),
        attendance_totals AS (SELECT b.trip_id,
        """+ATTENDANCE_COLUMNS+"""
          FROM cohort b JOIN booking_items bi ON bi.booking_id=b.id
          LEFT JOIN tickets tk ON tk.replaced=FALSE AND tk.booking_item_id=bi.id AND tk.booking_id=b.id
          LEFT JOIN ticket_boarding a ON a.booking_item_id=bi.id AND (a.ticket_id=tk.id OR (a.ticket_id IS NULL AND tk.id IS NULL))
          WHERE
        """+ELIGIBLE+"""
          GROUP BY b.trip_id), performance AS (
          SELECT t.*,bus.license_plate bus,COALESCE(b.bookings,0) bookings,COALESCE(k.valid_tickets,0) valid_tickets,
            COALESCE(p.gross,0) gross,COALESCE(r.refund,0) refund,COALESCE(p.paid_count,0) paid_count,COALESCE(r.refund_count,0) refund_count,
            COALESCE(a.boarded,0) boarded,COALESCE(a.no_show,0) no_show,COALESCE(a.checked,0) checked,
            COALESCE(a.unresolved,0) unresolved,COALESCE(a.ticketless,0) ticketless,
            l.expected,l.actual,l.sellable,l.reserved,l.paid,l.held,l.complete,l.whole_available
          FROM selected t JOIN buses bus ON bus.id=t.bus_id JOIN loads l ON l.trip_id=t.id
          LEFT JOIN booking_totals b ON b.trip_id=t.id LEFT JOIN ticket_totals k ON k.trip_id=t.id
          LEFT JOIN payment_totals p ON p.trip_id=t.id LEFT JOIN refund_totals r ON r.trip_id=t.id
          LEFT JOIN attendance_totals a ON a.trip_id=t.id)
        """;
    public long tripCount(ReportFilter f,long operator) {
        return jdbc.queryForObject(TRAVEL+"SELECT COUNT(*) FROM selected",f.parameters(operator),Long.class);
    }
    public List<TripPerformance> trips(ReportFilter f,long operator) {
        // Limit expensive matrix and domain aggregation to the requested page.
        String paged=PERFORMANCE.replace("AND t.departure_time>=:start AND t.departure_time<:end)",
                "AND t.departure_time>=:start AND t.departure_time<:end ORDER BY t.departure_time,t.id LIMIT :limit OFFSET :offset)");
        return jdbc.query(paged+"SELECT * FROM performance ORDER BY departure_time,id",f.parameters(operator),(rs,i)->new TripPerformance(
                rs.getLong("id"),rs.getLong("route_id"),rs.getString("route"),BusGoTime.api(JpaJdbcTime.read(rs,"departure_time")),
                rs.getString("status"),rs.getString("bus"),rs.getLong("bookings"),rs.getLong("valid_tickets"),attendance(rs),money(rs),load(rs,"CANCELLED".equals(rs.getString("status"))),
                rs.getBoolean("complete")?rs.getLong("whole_available"):null));
    }
    private static final String ROUTES = """
        SELECT rt.id,rt.name FROM operator_routes opr JOIN routes rt ON rt.id=opr.route_id
        WHERE opr.operator_id=:operator AND (:route IS NULL OR rt.id=:route)
          AND (:trip IS NULL OR EXISTS(SELECT 1 FROM trips t WHERE t.operator_route_id=opr.id AND t.id=:trip))
        """;
    public long routeCount(ReportFilter f,long operator) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ("+ROUTES+") r",f.parameters(operator),Long.class);
    }
    private static final String ROUTE_PERFORMANCE = PERFORMANCE.replace("JOIN routes rt ON rt.id=opr.route_id", "JOIN routes rt ON rt.id=opr.route_id JOIN route_page rp ON rp.id=rt.id")
                .replace("WITH selected AS", "WITH route_page AS ("+ROUTES+" ORDER BY rt.id LIMIT :limit OFFSET :offset), selected AS")+"""
          SELECT r.id route_id,r.name route,COUNT(p.id) trips,
            COALESCE(SUM(p.status IN ('DEPARTED','COMPLETED')),0) operated,
            COALESCE(SUM(p.bookings),0) bookings,COALESCE(SUM(p.valid_tickets),0) valid_tickets,
            COALESCE(SUM(p.gross),0) gross,COALESCE(SUM(p.refund),0) refund,
            COALESCE(SUM(p.paid_count),0) paid_count,COALESCE(SUM(p.refund_count),0) refund_count,
            COALESCE(SUM(p.boarded),0) boarded,COALESCE(SUM(p.no_show),0) no_show,
            COALESCE(SUM(p.checked),0) checked,COALESCE(SUM(p.unresolved),0) unresolved,COALESCE(SUM(p.ticketless),0) ticketless,
            COALESCE(SUM(CASE WHEN p.status<>'CANCELLED' THEN p.expected ELSE 0 END),0) expected,
            COALESCE(SUM(CASE WHEN p.status<>'CANCELLED' THEN p.actual ELSE 0 END),0) actual,
            COALESCE(SUM(CASE WHEN p.status<>'CANCELLED' THEN p.sellable ELSE 0 END),0) sellable,
            COALESCE(SUM(CASE WHEN p.status<>'CANCELLED' THEN p.reserved ELSE 0 END),0) reserved,
            COALESCE(SUM(CASE WHEN p.status<>'CANCELLED' THEN p.paid ELSE 0 END),0) paid,
            COALESCE(SUM(CASE WHEN p.status<>'CANCELLED' THEN p.held ELSE 0 END),0) held,
            COALESCE(MIN(CASE WHEN p.status<>'CANCELLED' THEN p.complete END),1) complete
          FROM route_page r LEFT JOIN performance p ON p.route_id=r.id GROUP BY r.id,r.name ORDER BY r.id
          """;
    public List<RoutePerformance> routes(ReportFilter f,long operator) {
        return jdbc.query(ROUTE_PERFORMANCE,f.parameters(operator),(rs,i)->new RoutePerformance(rs.getLong("route_id"),rs.getString("route"),rs.getLong("trips"),rs.getLong("operated"),
                rs.getLong("bookings"),rs.getLong("valid_tickets"),attendance(rs),money(rs),load(rs)));
    }
    public List<Map<String,Object>> explainTrips(ReportFilter f,long operator) {
        return jdbc.queryForList("EXPLAIN "+PERFORMANCE+"SELECT * FROM performance",f.parameters(operator));
    }
    public List<Map<String,Object>> explainRoutes(ReportFilter f,long operator) {
        return jdbc.queryForList("EXPLAIN "+ROUTE_PERFORMANCE,f.parameters(operator));
    }
    static Load load(ResultSet rs) throws SQLException {
        return load(rs,false);
    }
    static Load load(ResultSet rs,boolean excludedFromLoad) throws SQLException {
        long expected=rs.getLong("expected"),actual=rs.getLong("actual"),sellable=rs.getLong("sellable"),reserved=rs.getLong("reserved"),paid=rs.getLong("paid");
        boolean complete=rs.getBoolean("complete");
        return new Load(expected,actual,Math.max(0,expected-actual),sellable,reserved,paid,rs.getLong("held"),complete,
                complete && !excludedFromLoad?ratio(reserved,sellable):null,complete && !excludedFromLoad?ratio(paid,sellable):null);
    }
    static Attendance attendance(ResultSet rs) throws SQLException {
        long boarded=rs.getLong("boarded"),noShow=rs.getLong("no_show"),resolved=boarded+noShow;
        return new Attendance(resolved,boarded,noShow,rs.getLong("checked"),rs.getLong("unresolved"),rs.getLong("ticketless"),ratio(boarded,resolved),ratio(noShow,resolved));
    }
    public static Double ratio(long numerator,long denominator) { return denominator==0?null:(double)numerator/denominator; }
    static Money money(ResultSet rs) throws SQLException {
        BigDecimal gross=rs.getBigDecimal("gross"),refund=rs.getBigDecimal("refund");
        return new Money(gross,refund,gross.subtract(refund),rs.getLong("paid_count"),rs.getLong("refund_count"));
    }
    static Money zeroMoney() { return new Money(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,0,0); }
    static Money add(Money a,Money b) { return new Money(a.grossMockCollections().add(b.grossMockCollections()),a.mockRefunds().add(b.mockRefunds()),a.netMockCollections().add(b.netMockCollections()),a.paidPaymentCount()+b.paidPaymentCount(),a.refundedPaymentCount()+b.refundedPaymentCount()); }
    static Map<String,Long> counts(String... keys) { var map=new LinkedHashMap<String,Long>();for(String key:keys)map.put(key,0L);return map; }
}
