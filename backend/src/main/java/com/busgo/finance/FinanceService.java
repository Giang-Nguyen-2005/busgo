package com.busgo.finance;

import static com.busgo.finance.FinanceDtos.*;
import com.busgo.finance.FinanceDtos.Period;
import com.busgo.finance.FinanceDtos.Statement;
import static com.busgo.finance.CommissionMoney.*;
import com.busgo.audit.AuditService;
import com.busgo.common.exception.BusinessException;
import com.busgo.common.exception.ResourceNotFoundException;
import com.busgo.common.security.CurrentUser;
import com.busgo.common.time.JpaJdbcTime;
import com.busgo.operator.OperatorContextService;
import com.busgo.user.entity.RoleCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class FinanceService {
    private final JdbcTemplate db;
    private final FinancialLedger ledger;
    private final OperatorContextService context;
    private final AuditService audit;
    private final ObjectMapper json;
    public FinanceService(JdbcTemplate db,FinancialLedger ledger,OperatorContextService context,AuditService audit,ObjectMapper json) {
        this.db=db;this.ledger=ledger;this.context=context;this.audit=audit;this.json=json;
    }
    public Long scope(CurrentUser user,boolean platform,Long requested) {
        if(platform) {admin(user);if(requested!=null) requireOperator(requested);return requested;}
        long own=context.requireAdminOperator(user).getId();
        if(requested!=null && own!=requested) throw denied();
        return own;
    }
    static void admin(CurrentUser user) {
        if(user==null || !user.roles().contains(RoleCode.SYSTEM_ADMIN)) throw denied();
    }
    static BusinessException denied() {return new BusinessException("ACCESS_DENIED","Không có quyền truy cập tài chính.",HttpStatus.FORBIDDEN,null);}
    static BusinessException bad(String code,String message) {return new BusinessException(code,message,HttpStatus.BAD_REQUEST,null);}
    static ResourceNotFoundException missing() {return new ResourceNotFoundException("FINANCE_NOT_FOUND","Không tìm thấy dữ liệu tài chính.");}
    void requireOperator(long id) {
        if(db.queryForObject("SELECT COUNT(*) FROM transport_operators WHERE id=?",Long.class,id)!=1) throw missing();
    }
    static void page(int page,int size) {if(page<0 || page>10000 || size<1 || size>100) throw bad("FINANCE_PAGE_INVALID","Phân trang không hợp lệ.");}
    public Page<Operator> operators(CurrentUser user,int page,int size) {
        admin(user);page(page,size);
        return new Page<>(db.query("SELECT id,name FROM transport_operators ORDER BY id LIMIT ? OFFSET ?",
            (r,n)->new Operator(r.getLong(1),r.getString(2)),size,page*size),page,size,
            db.queryForObject("SELECT COUNT(*) FROM transport_operators",Long.class));
    }
    static Policy policy(ResultSet r,int n) throws SQLException {
        return new Policy(r.getLong("id"),r.getLong("operator_id"),r.getBigDecimal("rate"),FinancialLedger.time(r,"valid_from"),
            FinancialLedger.time(r,"valid_to"),r.getString("state"),r.getLong("created_by"),r.getString("reason"),
            FinancialLedger.time(r,"activated_at"));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<OperatorComparison> comparison(CurrentUser user,Period period,int page,int size) {
        var operators=operators(user,page,size);
        return new Page<>(operators.data().stream().map(o->new OperatorComparison(o.id(),o.name(),totals(o.id(),period).totals())).toList(),page,size,operators.total());
    }
    public Page<Policy> policies(CurrentUser user,boolean platform,Long operator,int page,int size) {
        Long own=scope(user,platform,operator);page(page,size);
        String where=own==null?"":" WHERE operator_id="+own;
        return new Page<>(db.query("SELECT * FROM commission_policies"+where+" ORDER BY valid_from DESC,id DESC LIMIT ? OFFSET ?",
            FinanceService::policy,size,page*size),page,size,db.queryForObject("SELECT COUNT(*) FROM commission_policies"+where,Long.class));
    }
    public Policy createPolicy(CurrentUser user,PolicyInput input) {
        admin(user);requireOperator(input.operatorId());
        var start=input.validFrom().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        var end=input.validTo()==null?null:input.validTo().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        if(input.rate()==null || input.rate().scale()>4 || input.rate().signum()<0 || input.rate().compareTo(new BigDecimal("100"))>0
            || start.isBefore(ledger.now()) || (end!=null && !end.isAfter(start)))
            throw bad("COMMISSION_TERMS_INVALID","Tỷ lệ từ 0–100%, tối đa 4 chữ số thập phân; thời điểm hiệu lực phải ở tương lai.");
        ledger.lock(input.operatorId());
        db.update("INSERT INTO commission_policies(operator_id,rate,valid_from,valid_to,created_by,reason,created_at) VALUES(?,?,?,?,?,?,?)",
            input.operatorId(),input.rate(),start,end,user.id(),input.reason().trim(),ledger.now());
        long id=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        audit.record(user,input.operatorId(),"COMMISSION_POLICY",id,"CREATED");
        return db.queryForObject("SELECT * FROM commission_policies WHERE id=?",FinanceService::policy,id);
    }
    public Policy activate(CurrentUser user,long id) {
        admin(user);
        var found=db.query("SELECT * FROM commission_policies WHERE id=?",FinanceService::policy,id);
        if(found.isEmpty()) throw missing();
        ledger.lock(found.get(0).operatorId());
        var policy=db.queryForObject("SELECT * FROM commission_policies WHERE id=? FOR UPDATE",FinanceService::policy,id);
        if("ACTIVE".equals(policy.state())) return policy;
        var start=policy.validFrom().toLocalDateTime();
        if(start.isBefore(ledger.now())) throw bad("COMMISSION_START_PASSED","Tạo chính sách mới với thời điểm hiệu lực trong tương lai.");
        var overlaps=db.query("""
            SELECT * FROM commission_policies WHERE operator_id=? AND state='ACTIVE'
             AND (valid_to IS NULL OR valid_to>?) AND (? IS NULL OR valid_from<?) ORDER BY id FOR UPDATE
            """,FinanceService::policy,policy.operatorId(),start,
            policy.validTo()==null?null:policy.validTo().toLocalDateTime(),policy.validTo()==null?null:policy.validTo().toLocalDateTime());
        // Replacement may close exactly one predecessor; never rewrite a future schedule.
        if(overlaps.size()>1 || overlaps.stream().anyMatch(p->!p.validFrom().isBefore(policy.validFrom())))
            throw bad("COMMISSION_WINDOW_OVERLAP","Khoảng hiệu lực trùng lịch đã kích hoạt.");
        for(var previous:overlaps) {
            db.update("UPDATE commission_policies SET valid_to=? WHERE id=?",start,previous.id());
            audit.record(user,policy.operatorId(),"COMMISSION_POLICY",previous.id(),"REPLACED");
        }
        db.update("UPDATE commission_policies SET state='ACTIVE',activated_at=? WHERE id=?",ledger.now(),id);
        audit.record(user,policy.operatorId(),"COMMISSION_POLICY",id,"ACTIVATED");
        return db.queryForObject("SELECT * FROM commission_policies WHERE id=?",FinanceService::policy,id);
    }
    private String where(Long operator) {return operator==null?"":" AND operator_id="+operator;}
    public Page<Event> events(CurrentUser user,boolean platform,Long operator,Period period,int page,int size) {
        return events(user,platform,operator,period,page,size,null,null);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Event> events(CurrentUser user,boolean platform,Long operator,Period period,int page,int size,Long bookingId,String channel) {
        Long own=scope(user,platform,operator);page(page,size);
        String where=" WHERE occurred_at>=? AND occurred_at<?"+where(own);
        if(bookingId!=null) {if(bookingId<=0) throw bad("FINANCE_FILTER_INVALID","Mã đặt vé không hợp lệ.");where+=" AND booking_id="+bookingId;}
        if(channel!=null && !channel.isBlank()) {
            if(!Set.of("PLATFORM_MEDIATED_SIMULATED","OPERATOR_RECORDED_SIMULATED","UNKNOWN_OR_UNVERIFIED").contains(channel))
                throw bad("FINANCE_FILTER_INVALID","Bên thu không hợp lệ.");
            where+=" AND channel='"+channel+"'";
        }
        return new Page<>(db.query("SELECT * FROM financial_events"+where+" ORDER BY occurred_at DESC,id DESC LIMIT ? OFFSET ?",
            FinancialLedger::event,period.start(),period.end(),size,page*size),page,size,
            db.queryForObject("SELECT COUNT(*) FROM financial_events"+where,Long.class,period.start(),period.end()));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Overview overview(CurrentUser user,boolean platform,Long operator,Period period) {
        return totals(scope(user,platform,operator),period);
    }
    private Overview totals(Long operator,Period period) {
        String filter=where(operator);
        var sums=db.queryForMap("""
            SELECT COALESCE(SUM(CASE WHEN event_type='COLLECTION' THEN amount ELSE 0 END),0) collections,
            COALESCE(-SUM(CASE WHEN event_type='REFUND' THEN amount ELSE 0 END),0) refunds,
            COALESCE(SUM(CASE WHEN event_type='COLLECTION' THEN commission ELSE 0 END),0) accrual,
            COALESCE(-SUM(CASE WHEN event_type='REFUND' THEN commission ELSE 0 END),0) reversal,
            COALESCE(SUM(operator_payable),0) payable,COALESCE(SUM(platform_receivable),0) receivable,
            COALESCE(SUM(CASE WHEN channel='UNKNOWN_OR_UNVERIFIED' THEN amount ELSE 0 END),0) unknown_amount,
            COALESCE(SUM(CASE WHEN commission IS NULL THEN amount ELSE 0 END),0) unpriced,
            COALESCE(SUM(gross_context),0) gross,COALESCE(SUM(discount_context),0) discount,COUNT(*) events,
            COALESCE(SUM(channel='UNKNOWN_OR_UNVERIFIED'),0) unknown_count,COALESCE(SUM(commission IS NULL),0) unpriced_count
            FROM financial_events WHERE occurred_at>=? AND occurred_at<?
            """+filter,period.start(),period.end());
        String sourceFilter=operator==null?"":" AND o.operator_id="+operator;
        var source=db.queryForMap("""
            SELECT COUNT(*) total,COALESCE(SUM(CASE WHEN e.id IS NULL THEN 1 ELSE 0 END),0) missing,
             COALESCE(SUM(sources.collection),0) collections,COALESCE(SUM(sources.refund),0) refunds FROM (
             SELECT CONCAT('payment:',p.id) source_key,p.amount collection,0 refund FROM payments p JOIN bookings b ON b.id=p.booking_id
             JOIN trips t ON t.id=b.trip_id JOIN operator_routes o ON o.id=t.operator_route_id
             WHERE p.paid_at>=? AND p.paid_at<? AND p.status IN ('PAID','REFUNDED')
            """+sourceFilter+"""
             UNION ALL
             SELECT CONCAT('refund:',r.id) source_key,0 collection,r.amount refund FROM refunds r JOIN payments p ON p.id=r.payment_id
             JOIN bookings b ON b.id=p.booking_id JOIN trips t ON t.id=b.trip_id JOIN operator_routes o ON o.id=t.operator_route_id
             WHERE r.refunded_at>=? AND r.refunded_at<?
            """+sourceFilter+") sources LEFT JOIN financial_events e ON e.source_key=sources.source_key",
            JpaJdbcTime.parameter(period.start()),JpaJdbcTime.parameter(period.end()),JpaJdbcTime.parameter(period.start()),JpaJdbcTime.parameter(period.end()));
        BigDecimal collection=money(sums,"collections"),refund=money(sums,"refunds"),accrual=money(sums,"accrual"),reversal=money(sums,"reversal");
        return new Overview(period,"Asia/Ho_Chi_Minh","VND","SIMULATED_INTERNAL",operator,
            operator==null?"Tất cả nhà xe":db.queryForObject("SELECT name FROM transport_operators WHERE id=?",String.class,operator),"NOT_CHECKED",
            new Totals(collection,refund,collection.subtract(refund),accrual,reversal,accrual.subtract(reversal),
                money(sums,"payable"),money(sums,"receivable"),money(sums,"unknown_amount"),money(sums,"unpriced"),
                money(sums,"gross"),money(sums,"discount"),((Number)sums.get("events")).longValue(),
                ((Number)source.get("total")).longValue(),((Number)source.get("missing")).longValue(),money(source,"collections"),money(source,"refunds"),
                ((Number)sums.get("unknown_count")).longValue(),((Number)sums.get("unpriced_count")).longValue()));
    }
    private static BigDecimal money(Map<String,Object> map,String key) {return new BigDecimal(map.get(key).toString()).setScale(2);}
    private static boolean equal(BigDecimal a,BigDecimal b) {return a==null?b==null:b!=null&&a.compareTo(b)==0;}
    private String encode(Object value) {try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private Overview decode(String value) {try{return json.readValue(value,Overview.class);}catch(Exception e){throw new IllegalStateException(e);}}
    public Run reconcile(CurrentUser user,RunInput input) {
        admin(user);requireOperator(input.operatorId());var period=new Period(input.from(),input.to());
        ledger.lock(input.operatorId());
        var started=ledger.now();var sources=ledger.periodSources(input.operatorId(),period);
        int projected=0;
        if(input.projectMissing()) for(var source:sources) projected+=ledger.project(source);
        var errors=new LinkedHashSet<String>();
        var keys=new HashSet<String>();
        for(var p:sources) {
            String key="payment:"+p.payment();keys.add(key);
            var existing=ledger.eventsByKey(key,false);
            Event original=existing.isEmpty()?null:existing.get(0);
            String route=channel(p.source(),p.method(),p.purpose(),p.actor());
            if(inPeriod(p.paidAt(),period)) check(errors,key,original,p,p.amount(),p.paidAt(),route,null);
            if(original!=null) {
                var policies=db.query("""
                    SELECT * FROM commission_policies WHERE operator_id=? AND state='ACTIVE'
                    AND valid_from<=? AND (valid_to IS NULL OR valid_to>?) AND activated_at<=?
                    """,FinanceService::policy,p.operator(),p.paidAt(),p.paidAt(),p.paidAt());
                var expected=policies.isEmpty()?null:policies.get(0);
                if(!Objects.equals(original.policyId(),expected==null?null:expected.id())
                    || !equal(original.rate(),expected==null?null:expected.rate())
                    || !equal(original.commission(),expected==null?null:accrue(p.amount(),expected.rate())))
                    errors.add(key+"|POLICY_SNAPSHOT_MISMATCH");
            }
            var refunds=db.query("SELECT id,amount,refunded_at FROM refunds WHERE payment_id=? ORDER BY id",
                (r,n)->new FinancialLedger.Refund(r.getLong("id"),r.getBigDecimal("amount"),JpaJdbcTime.read(r,"refunded_at")),p.payment());
            BigDecimal refunded=ZERO,reversed=ZERO;
            for(var r:refunds) {
                key="refund:"+r.id();keys.add(key);
                BigDecimal reversal=null;
                try {
                    if(original!=null && original.commission()!=null)
                        reversal=reversal(p.amount(),original.commission(),refunded,reversed,r.amount(),original.rate());
                    else if(refunded.add(r.amount()).compareTo(p.amount())>0) errors.add(key+"|REFUND_ALLOCATION_MISMATCH");
                } catch(IllegalArgumentException ex) {errors.add(key+"|REFUND_ALLOCATION_MISMATCH");}
                var rows=ledger.eventsByKey(key,false);
                Event event=rows.isEmpty()?null:rows.get(0);
                if(inPeriod(r.time(),period)) {
                    check(errors,key,event,p,r.amount().negate(),r.time(),route,r.id());
                    if(event!=null && (original==null || !Objects.equals(event.policyId(),original.policyId())
                        || !equal(event.rate(),original.rate()) || !equal(event.commission(),reversal==null?null:reversal.negate())))
                        errors.add(key+"|REFUND_ALLOCATION_MISMATCH");
                }
                refunded=refunded.add(r.amount());if(reversal!=null) reversed=reversed.add(reversal);
            }
        }
        var periodEvents=db.query("SELECT * FROM financial_events WHERE operator_id=? AND occurred_at>=? AND occurred_at<?",
            FinancialLedger::event,input.operatorId(),period.start(),period.end());
        for(var event:periodEvents) if(!keys.contains(event.sourceKey())) errors.add(event.sourceKey()+"|SOURCE_LINK_MISMATCH");
        var totals=totals(input.operatorId(),period);
        var snapshot=new Overview(totals.period(),totals.timezone(),totals.currency(),totals.verification(),totals.operatorId(),
            totals.operatorName(),errors.isEmpty()?"CLEAN":"EXCEPTIONS",totals.totals());
        db.update("""
            INSERT INTO financial_runs(operator_id,start_date,end_date,status,source_count,projected_count,exception_count,
             created_by,started_at,completed_at,snapshot) VALUES(?,?,?,?,?,?,?,?,?,?,?)
            """,input.operatorId(),input.from(),input.to(),errors.isEmpty()?"CLEAN":"EXCEPTIONS",
            snapshot.totals().sourceCount(),projected,errors.size(),user.id(),started,ledger.now(),encode(snapshot));
        long id=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        for(String error:errors) {String[] parts=error.split("\\|");db.update("INSERT INTO financial_exceptions(run_id,source_key,reason) VALUES(?,?,?)",id,parts[0],parts[1]);}
        audit.record(user,input.operatorId(),"FINANCE_RECONCILIATION",id,"COMPLETED");
        return run(user,true,input.operatorId(),id);
    }
    static boolean inPeriod(LocalDateTime time,Period period) {return !time.isBefore(period.start())&&time.isBefore(period.end());}
    private void check(Set<String> errors,String key,Event event,FinancialLedger.Source p,BigDecimal amount,LocalDateTime time,String route,Long refund) {
        if(event==null) {errors.add(key+"|MISSING_EVENT");return;}
        if(event.paymentId()!=p.payment() || event.bookingId()!=p.booking() || event.operatorId()!=p.operator()
            || !Objects.equals(event.refundId(),refund) || !event.purpose().equals(p.purpose())
            || !event.eventType().equals(refund==null?"COLLECTION":"REFUND")) errors.add(key+"|SOURCE_LINK_MISMATCH");
        if(!equal(event.amount(),amount) || !event.occurredAt().toLocalDateTime().equals(time)) errors.add(key+"|SOURCE_AMOUNT_OR_TIME_MISMATCH");
        if(!event.channel().equals(route)) errors.add(key+"|CHANNEL_MISMATCH");
        if("UNKNOWN_OR_UNVERIFIED".equals(route)) errors.add(key+"|CHANNEL_UNVERIFIED");
        if(event.commission()==null) errors.add(key+"|COMMISSION_UNPRICED");
        if(refund==null && "BOOKING".equals(p.purpose()) && event.grossContext()==null)
            errors.add(key+"|FARE_CONTEXT_UNAVAILABLE");
        BigDecimal payable=null,receivable=null;
        if(event.commission()!=null && !"UNKNOWN_OR_UNVERIFIED".equals(route)) {
            payable="PLATFORM_MEDIATED_SIMULATED".equals(route)?amount.subtract(event.commission()):ZERO;
            receivable="OPERATOR_RECORDED_SIMULATED".equals(route)?event.commission():ZERO;
        }
        if(!equal(event.operatorPayable(),payable) || !equal(event.platformReceivable(),receivable)) errors.add(key+"|ALLOCATION_IMBALANCE");
    }
    private Run mapRun(ResultSet r,int n) throws SQLException {
        return new Run(r.getLong("id"),r.getLong("operator_id"),r.getObject("start_date",LocalDate.class),r.getObject("end_date",LocalDate.class),
            r.getString("status"),r.getInt("source_count"),r.getInt("projected_count"),r.getInt("exception_count"),
            FinancialLedger.time(r,"started_at"),FinancialLedger.time(r,"completed_at"),decode(r.getString("snapshot")));
    }
    public Page<Run> runs(CurrentUser user,boolean platform,Long operator,int page,int size) {
        Long own=scope(user,platform,operator);page(page,size);String where=" WHERE 1=1"+where(own);
        return new Page<>(db.query("SELECT * FROM financial_runs"+where+" ORDER BY id DESC LIMIT ? OFFSET ?",this::mapRun,size,page*size),
            page,size,db.queryForObject("SELECT COUNT(*) FROM financial_runs"+where,Long.class));
    }
    public Run run(CurrentUser user,boolean platform,Long operator,long id) {
        Long own=scope(user,platform,operator);
        var rows=db.query("SELECT * FROM financial_runs WHERE id=?"+where(own),this::mapRun,id);
        if(rows.isEmpty()) throw missing();return rows.get(0);
    }
    public Page<ExceptionItem> exceptions(CurrentUser user,boolean platform,Long operator,long id,int page,int size) {
        run(user,platform,operator,id);page(page,size);
        return new Page<>(db.query("SELECT * FROM financial_exceptions WHERE run_id=? ORDER BY id LIMIT ? OFFSET ?",
            (r,n)->new ExceptionItem(r.getLong("id"),r.getString("source_key"),r.getString("reason")),id,size,page*size),
            page,size,db.queryForObject("SELECT COUNT(*) FROM financial_exceptions WHERE run_id=?",Long.class,id));
    }
    public Statement issue(CurrentUser user,long runId) {
        admin(user);Run run=run(user,true,null,runId);ledger.lock(run.operatorId());
        var existing=db.query("SELECT * FROM financial_statements WHERE run_id=?",this::mapStatement,runId);
        if(!existing.isEmpty()) return existing.get(0);
        db.update("INSERT INTO financial_statements(operator_id,run_id,created_by,issued_at,snapshot) VALUES(?,?,?,?,?)",
            run.operatorId(),runId,user.id(),ledger.now(),encode(run.snapshot()));
        long id=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        audit.record(user,run.operatorId(),"FINANCIAL_STATEMENT",id,"ISSUED");
        return statement(user,true,null,id);
    }
    private Statement mapStatement(ResultSet r,int n) throws SQLException {
        return new Statement(r.getLong("id"),r.getLong("operator_id"),r.getLong("run_id"),FinancialLedger.time(r,"issued_at"),decode(r.getString("snapshot")));
    }
    public Page<Statement> statements(CurrentUser user,boolean platform,Long operator,int page,int size) {
        Long own=scope(user,platform,operator);page(page,size);String where=" WHERE 1=1"+where(own);
        return new Page<>(db.query("SELECT * FROM financial_statements"+where+" ORDER BY id DESC LIMIT ? OFFSET ?",this::mapStatement,size,page*size),
            page,size,db.queryForObject("SELECT COUNT(*) FROM financial_statements"+where,Long.class));
    }
    public Statement statement(CurrentUser user,boolean platform,Long operator,long id) {
        Long own=scope(user,platform,operator);var rows=db.query("SELECT * FROM financial_statements WHERE id=?"+where(own),this::mapStatement,id);
        if(rows.isEmpty()) throw missing();return rows.get(0);
    }
}
