package com.busgo.customer;

import com.busgo.common.response.PagedResponse;
import com.busgo.common.response.PagedResponse.Pagination;
import com.busgo.common.time.JpaJdbcTime;
import com.busgo.customer.CustomerDtos.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerRepository {
    // Every domain is reduced to booking grain BEFORE money and attendance are combined.
    private static final String COHORT = """
        WITH owned AS (
          SELECT b.id,b.customer_id,b.trip_id,b.booking_code,b.contact_name,b.contact_phone,b.contact_email,
              b.source,b.payment_method,b.status,b.created_at,b.cancelled_at,b.cancellation_reason,
              CASE WHEN b.customer_id IS NULL THEN CONCAT('CONTACT:',b.id)
              ELSE CONCAT('ACCOUNT:',b.customer_id) END AS customer_key,
              CASE WHEN b.customer_id IS NULL THEN 'OFFLINE_CONTACT' ELSE 'ACCOUNT' END AS customer_type,
              r.name AS route_name, pl.name AS pickup, dl.name AS dropoff
          FROM bookings b JOIN trips t ON t.id=b.trip_id
          JOIN operator_routes opr ON opr.id=t.operator_route_id
          JOIN routes r ON r.id=opr.route_id
          JOIN trip_stops ps ON ps.id=b.pickup_trip_stop_id JOIN locations pl ON pl.id=ps.location_id
          JOIN trip_stops ds ON ds.id=b.dropoff_trip_stop_id JOIN locations dl ON dl.id=ds.location_id
          WHERE opr.operator_id=:operatorId
            AND (:key IS NULL OR b.customer_id=:accountId OR (b.customer_id IS NULL AND b.id=:contactId))
        ), paid AS (
          SELECT p.booking_id, SUM(p.amount) AS gross FROM payments p JOIN owned o ON o.id=p.booking_id
          WHERE p.status IN ('PAID','REFUNDED') AND p.paid_at IS NOT NULL GROUP BY p.booking_id
        ), refunded AS (
          SELECT p.booking_id, SUM(f.amount) AS refunds FROM refunds f JOIN payments p ON p.id=f.payment_id
          JOIN owned o ON o.id=p.booking_id GROUP BY p.booking_id
        ), items AS (
          SELECT bi.booking_id, GROUP_CONCAT(bi.seat_code ORDER BY bi.id SEPARATOR ', ') AS seats,
            SUM(CASE WHEN tk.status='VALID' THEN 1 ELSE 0 END) AS valid_tickets,
            SUM(CASE WHEN tk.status='VOID' THEN 1 ELSE 0 END) AS void_tickets,
            SUM(CASE WHEN a.status='BOARDED' THEN 1 ELSE 0 END) AS boarded,
            SUM(CASE WHEN a.status='NO_SHOW' THEN 1 ELSE 0 END) AS no_show,
            SUM(CASE WHEN a.status='CHECKED_IN' THEN 1 ELSE 0 END) AS checked_in,
            SUM(CASE WHEN a.status IS NULL OR a.status='EXPECTED' THEN 1 ELSE 0 END) AS unrecorded
          FROM booking_items bi JOIN owned o ON o.id=bi.booking_id
          LEFT JOIN tickets tk ON tk.booking_item_id=bi.id
          LEFT JOIN ticket_boarding a ON a.booking_item_id=bi.id GROUP BY bi.booking_id
        ), activity AS (
          SELECT o.*, COALESCE(p.gross,0) AS gross, COALESCE(f.refunds,0) AS refunds,
            COALESCE(i.boarded,0) AS boarded, COALESCE(i.no_show,0) AS no_show,
            COALESCE(i.checked_in,0) AS checked_in, COALESCE(i.unrecorded,0) AS unrecorded,
            i.seats, COALESCE(i.valid_tickets,0) AS valid_tickets, COALESCE(i.void_tickets,0) AS void_tickets
          FROM owned o LEFT JOIN paid p ON p.booking_id=o.id LEFT JOIN refunded f ON f.booking_id=o.id
          LEFT JOIN items i ON i.booking_id=o.id
        ), totals AS (
          SELECT customer_key, COUNT(*) AS total_bookings,
            SUM(status='CONFIRMED') AS confirmed_bookings, SUM(status='CANCELLED') AS cancelled_bookings,
            SUM(source='WEB') AS web_bookings, SUM(source='PHONE') AS phone_bookings,
            COUNT(DISTINCT CASE WHEN boarded>0 THEN trip_id END) AS boarded_journeys,
            SUM(gross) AS gross, SUM(refunds) AS refunds, SUM(boarded) AS boarded,
            SUM(no_show) AS no_show, SUM(checked_in) AS checked_in, SUM(unrecorded) AS unrecorded
          FROM activity GROUP BY customer_key
        ), ranked AS (
          SELECT o.*, ROW_NUMBER() OVER(PARTITION BY customer_key ORDER BY created_at DESC,id DESC) AS rn
          FROM owned o
        ), directory AS (
          SELECT r.customer_key,r.customer_type,r.contact_name,r.contact_phone,r.contact_email,
            r.created_at, CONCAT(r.pickup,' → ',r.dropoff) AS journey, t.total_bookings,
            t.confirmed_bookings,t.cancelled_bookings,t.web_bookings,t.phone_bookings,t.boarded_journeys,
            t.gross,t.refunds,t.boarded,t.no_show,t.checked_in,t.unrecorded
          FROM ranked r JOIN totals t ON t.customer_key=r.customer_key WHERE r.rn=1
            AND (:type IS NULL OR r.customer_type=:type)
            AND (:q IS NULL OR EXISTS (SELECT 1 FROM owned s WHERE s.customer_key=r.customer_key
              AND (LOWER(s.contact_name) LIKE :q ESCAPE '!' OR LOWER(s.contact_phone) LIKE :q ESCAPE '!'
                OR LOWER(s.contact_email) LIKE :q ESCAPE '!' OR LOWER(s.booking_code) LIKE :q ESCAPE '!')))
        )
        """;
    private final NamedParameterJdbcTemplate jdbc;
    public CustomerRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    private MapSqlParameterSource params(long operator, String key, CustomerFilter f) {
        return new MapSqlParameterSource("operatorId",operator).addValue("key",key)
            .addValue("accountId",key!=null&&key.startsWith("ACCOUNT:")?Long.parseLong(key.substring(8)):null)
            .addValue("contactId",key!=null&&key.startsWith("CONTACT:")?Long.parseLong(key.substring(8)):null)
            .addValue("type",f.customerType()==null?null:f.customerType().name()).addValue("q",f.search())
            .addValue("limit",f.pageSize()).addValue("offset",f.pageNumber()*f.pageSize());
    }
    public PagedResponse<Summary> directory(long operator, CustomerFilter f) {
        var p=params(operator,null,f);
        long count=jdbc.queryForObject(COHORT+"SELECT COUNT(*) FROM directory",p,Long.class);
        String order=switch(f.ordering()) {
            case LATEST -> "created_at DESC";
            case NAME -> "LOWER(contact_name) ASC";
            case BOOKINGS -> "total_bookings DESC";
            case MOCK_PAID -> "gross DESC";
        };
        var rows=jdbc.query(COHORT+"SELECT * FROM directory ORDER BY "+order+",customer_key ASC LIMIT :limit OFFSET :offset",p,(rs,n)->summary(rs));
        return new PagedResponse<>(rows,pagination(f,count));
    }
    public Optional<Summary> summary(long operator,String key,CustomerFilter f) {
        return jdbc.query(COHORT+"SELECT * FROM directory",params(operator,key,f),(rs,n)->summary(rs)).stream().findFirst();
    }
    public PagedResponse<BookingHistory> history(long operator,String key,CustomerFilter f,long count) {
        var p=params(operator,key,f);
        var ids=jdbc.queryForList(COHORT+"SELECT id FROM owned ORDER BY created_at DESC,id DESC LIMIT :limit OFFSET :offset",p,Long.class);
        if(ids.isEmpty()) return new PagedResponse<>(List.of(),pagination(f,count));
        p.addValue("ids",ids);
        Map<Long,List<PaymentEvent>> payments=new HashMap<>();
        Map<Long,List<RefundEvent>> refunds=new HashMap<>();
        jdbc.query(COHORT+"SELECT p.* FROM payments p JOIN owned o ON o.id=p.booking_id WHERE o.id IN (:ids) ORDER BY p.created_at,p.id",p,
            (org.springframework.jdbc.core.RowCallbackHandler) rs -> payments.computeIfAbsent(rs.getLong("booking_id"),id->new ArrayList<>()).add(
                new PaymentEvent(rs.getLong("id"),rs.getString("method"),rs.getString("status"),rs.getBigDecimal("amount"),time(rs,"paid_at"),time(rs,"created_at"))));
        jdbc.query(COHORT+"SELECT f.*,p.booking_id FROM refunds f JOIN payments p ON p.id=f.payment_id JOIN owned o ON o.id=p.booking_id WHERE o.id IN (:ids) ORDER BY f.refunded_at,f.id",p,
            (org.springframework.jdbc.core.RowCallbackHandler) rs -> refunds.computeIfAbsent(rs.getLong("booking_id"),id->new ArrayList<>()).add(
                new RefundEvent(rs.getLong("id"),rs.getBigDecimal("amount"),rs.getString("reason_code"),time(rs,"refunded_at"))));
        var rows=jdbc.query(COHORT+"""
            SELECT a.*, COALESCE(p.status,'PENDING') AS payment_status FROM activity a
            LEFT JOIN payments p ON p.id=(SELECT MAX(p2.id) FROM payments p2 WHERE p2.booking_id=a.id)
            ORDER BY a.created_at DESC,a.id DESC LIMIT :limit OFFSET :offset
            """,p,(rs,n)->new BookingHistory(rs.getLong("id"),rs.getString("booking_code"),
                rs.getString("source"),rs.getString("contact_name"),rs.getString("contact_phone"),rs.getString("contact_email"),
                rs.getString("route_name"),rs.getString("pickup"),rs.getString("dropoff"),rs.getString("seats"),
                rs.getString("status"),rs.getString("payment_method"),rs.getString("payment_status"),
                rs.getLong("valid_tickets"),rs.getLong("void_tickets"),attendance(rs),money(rs),
                rs.getString("cancellation_reason"),time(rs,"cancelled_at"),time(rs,"created_at"),
                payments.getOrDefault(rs.getLong("id"),List.of()),refunds.getOrDefault(rs.getLong("id"),List.of())));
        return new PagedResponse<>(rows,pagination(f,count));
    }
    private static Pagination pagination(CustomerFilter f,long count) {
        return new Pagination(f.pageNumber(),f.pageSize(),count,(int)((count+f.pageSize()-1)/f.pageSize()));
    }
    private static Summary summary(ResultSet rs) throws SQLException {
        return new Summary(rs.getString("customer_key"),Type.valueOf(rs.getString("customer_type")),
            rs.getString("contact_name"),rs.getString("contact_phone"),rs.getString("contact_email"),
            time(rs,"created_at"),rs.getString("journey"),rs.getLong("total_bookings"),
            rs.getLong("confirmed_bookings"),rs.getLong("cancelled_bookings"),rs.getLong("web_bookings"),
            rs.getLong("phone_bookings"),rs.getLong("boarded_journeys"),attendance(rs),money(rs));
    }
    private static Attendance attendance(ResultSet rs) throws SQLException {
        return new Attendance(rs.getLong("boarded"),rs.getLong("no_show"),rs.getLong("checked_in"),rs.getLong("unrecorded"));
    }
    private static Money money(ResultSet rs) throws SQLException {
        var gross=rs.getBigDecimal("gross");var refunds=rs.getBigDecimal("refunds");return new Money(gross,refunds,gross.subtract(refunds));
    }
    private static java.time.OffsetDateTime time(ResultSet rs,String column) throws SQLException {
        var value=JpaJdbcTime.read(rs,column);return value==null?null:value.atOffset(java.time.ZoneOffset.UTC);
    }
}
