package com.busgo.finance;

import static com.busgo.finance.CommissionMoney.*;
import static com.busgo.finance.FinanceDtos.*;
import com.busgo.finance.FinanceDtos.Period;
import com.busgo.common.time.JpaJdbcTime;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** No commerce locks may be acquired after entering this service. */
@Service
public class FinancialLedger {
    final JdbcTemplate db;
    final Clock clock;
    public FinancialLedger(JdbcTemplate db,Clock clock) {this.db=db;this.clock=clock;}
    LocalDateTime now() {return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);}
    void lock(long operator) {
        db.update("INSERT INTO finance_mutex(operator_id) VALUES(?) ON DUPLICATE KEY UPDATE operator_id=operator_id",operator);
    }
    record Source(long payment,long booking,long operator,String source,String method,String purpose,String actor,
                  BigDecimal amount,LocalDateTime paidAt,BigDecimal gross,BigDecimal discount) {}
    static final String SOURCES="""
        SELECT p.id,p.booking_id,o.operator_id,b.source,p.method,p.purpose,m.actor_type,p.amount,p.paid_at,
          b.original_gross_amount,b.original_discount_amount
        FROM payments p JOIN bookings b ON b.id=p.booking_id JOIN trips t ON t.id=b.trip_id
        JOIN operator_routes o ON o.id=t.operator_route_id
        LEFT JOIN booking_modifications m ON m.id=p.modification_id
        WHERE p.paid_at IS NOT NULL AND p.status IN ('PAID','REFUNDED')
        """;
    Source source(ResultSet r,int n) throws SQLException {
        return new Source(r.getLong("id"),r.getLong("booking_id"),r.getLong("operator_id"),r.getString("source"),
            r.getString("method"),r.getString("purpose"),r.getString("actor_type"),r.getBigDecimal("amount"),
            JpaJdbcTime.read(r,"paid_at"),r.getBigDecimal("original_gross_amount"),r.getBigDecimal("original_discount_amount"));
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void postBooking(long booking) {
        var rows=db.query(SOURCES+" AND b.id=? ORDER BY p.id",this::source,booking);
        if(rows.isEmpty()) return;
        lock(rows.get(0).operator());
        for(var row:rows) project(row);
    }
    List<Source> periodSources(long operator,Period period) {
        // Include parent payments of refunds in this period even when paid in an earlier period.
        var rows=db.query(SOURCES+"""
             AND o.operator_id=? AND ((p.paid_at>=? AND p.paid_at<?)
             OR EXISTS(SELECT 1 FROM refunds r WHERE r.payment_id=p.id AND r.refunded_at>=? AND r.refunded_at<?))
             ORDER BY p.id LIMIT 5001
            """,this::source,operator,JpaJdbcTime.parameter(period.start()),JpaJdbcTime.parameter(period.end()),
            JpaJdbcTime.parameter(period.start()),JpaJdbcTime.parameter(period.end()));
        if(rows.size()>5000) throw FinanceService.bad("FINANCE_PERIOD_TOO_LARGE","Thu hẹp kỳ báo cáo (tối đa 5000 khoản thu).");
        return rows;
    }
    int project(Source p) {
        int inserted=0;
        var found=eventsByKey("payment:"+p.payment(),true);
        Event original;
        if(found.isEmpty()) {
            var policies=db.query("""
                SELECT * FROM commission_policies WHERE operator_id=? AND state='ACTIVE'
                AND valid_from<=? AND (valid_to IS NULL OR valid_to>?) AND activated_at<=?
                ORDER BY valid_from DESC,id DESC FOR UPDATE
                """,FinanceService::policy,p.operator(),p.paidAt(),p.paidAt(),p.paidAt());
            Policy policy=policies.isEmpty()?null:policies.get(0);
            String route=channel(p.source(),p.method(),p.purpose(),p.actor());
            var commission=policy==null?null:accrue(p.amount(),policy.rate());
            insert(p,"payment:"+p.payment(),"COLLECTION",null,p.amount(),route,policy==null?null:policy.id(),
                policy==null?null:policy.rate(),commission,p.paidAt(),
                contextKnown(p)?p.gross():null,contextKnown(p)?p.discount():null);
            original=eventsByKey("payment:"+p.payment(),true).get(0);inserted++;
        } else original=found.get(0);
        // All refunds for a source payment are ordered by immutable source ID.
        // Read with the established JPA/JDBC UTC bridge, not the JVM default timezone.
        var refunds=db.query("SELECT id,amount,refunded_at FROM refunds WHERE payment_id=? ORDER BY id",
            (r,n)->new Refund(r.getLong("id"),r.getBigDecimal("amount"),JpaJdbcTime.read(r,"refunded_at")),p.payment());
        BigDecimal refunded=ZERO,reversed=ZERO;
        for(var r:refunds) {
            var existing=eventsByKey("refund:"+r.id(),true);
            BigDecimal reversal=original.commission()==null?null:
                reversal(p.amount(),original.commission(),refunded,reversed,r.amount(),original.rate());
            if(existing.isEmpty()) {
                insert(p,"refund:"+r.id(),"REFUND",r.id(),r.amount().negate(),original.channel(),original.policyId(),
                    original.rate(),reversal==null?null:reversal.negate(),r.time(),null,null);
                inserted++;
            }
            refunded=refunded.add(r.amount());
            if(reversal!=null) reversed=reversed.add(reversal);
        }
        return inserted;
    }
    record Refund(long id,BigDecimal amount,LocalDateTime time) {}
    private boolean contextKnown(Source p) {
        return "BOOKING".equals(p.purpose()) && p.gross()!=null && p.discount()!=null
            && p.gross().subtract(p.discount()).compareTo(p.amount())==0;
    }
    void insert(Source p,String key,String type,Long refund,BigDecimal amount,String route,Long policy,
                BigDecimal rate,BigDecimal commission,LocalDateTime at,BigDecimal gross,BigDecimal discount) {
        BigDecimal payable=null,receivable=null;
        if(commission!=null && !"UNKNOWN_OR_UNVERIFIED".equals(route)) {
            payable="PLATFORM_MEDIATED_SIMULATED".equals(route)?amount.subtract(commission):ZERO;
            receivable="OPERATOR_RECORDED_SIMULATED".equals(route)?commission:ZERO;
        }
        db.update("""
            INSERT INTO financial_events(source_key,event_type,operator_id,booking_id,payment_id,refund_id,purpose,origin,
             channel,amount,policy_id,rate,commission,operator_payable,platform_receivable,gross_context,discount_context,occurred_at,created_at)
             VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,key,type,p.operator(),p.booking(),p.payment(),refund,p.purpose(),
            p.source()+("BOOKING_MODIFICATION".equals(p.purpose())?"_MODIFICATION":""),route,amount,policy,rate,
            commission,payable,receivable,gross,discount,at,now());
    }
    List<Event> eventsByKey(String key,boolean lock) {
        return db.query("SELECT * FROM financial_events WHERE source_key=?"+(lock?" FOR UPDATE":""),FinancialLedger::event,key);
    }
    static OffsetDateTime time(ResultSet r,String column) throws SQLException {
        var t=r.getObject(column,LocalDateTime.class);return t==null?null:t.atOffset(ZoneOffset.UTC);
    }
    static Event event(ResultSet r,int n) throws SQLException {
        return new Event(r.getLong("id"),r.getString("source_key"),r.getString("event_type"),r.getLong("operator_id"),
            r.getLong("booking_id"),r.getLong("payment_id"),r.getObject("refund_id",Long.class),r.getString("purpose"),
            r.getString("origin"),r.getString("channel"),r.getBigDecimal("amount"),r.getObject("policy_id",Long.class),
            r.getBigDecimal("rate"),r.getBigDecimal("commission"),r.getBigDecimal("operator_payable"),
            r.getBigDecimal("platform_receivable"),r.getBigDecimal("gross_context"),r.getBigDecimal("discount_context"),
            time(r,"occurred_at"),time(r,"created_at"),r.getString("currency"),r.getString("verification"));
    }
}
