package com.busgo.promotion;

import static com.busgo.common.time.BusGoTime.*;
import static com.busgo.common.time.JpaJdbcTime.*;
import static com.busgo.promotion.VoucherDtos.*;
import com.busgo.booking.entity.Booking;
import com.busgo.common.exception.*;
import com.busgo.common.response.PagedResponse;
import com.busgo.common.security.CurrentUser;
import com.busgo.operator.OperatorContextService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class VoucherService {
    private final JdbcTemplate db;
    private final OperatorContextService context;
    private final Clock clock;
    public VoucherService(JdbcTemplate db, OperatorContextService context, Clock clock) {
        this.db=db; this.context=context; this.clock=clock;
    }
    private LocalDateTime now() { return utc(clock.instant()); }
    public static String normalize(String code) {
        String result=code==null?"":code.strip().toUpperCase(Locale.ROOT);
        if(!result.matches("[A-Z0-9][A-Z0-9_-]{2,39}")) throw error("VOUCHER_CODE_INVALID","Mã ưu đãi gồm 3–40 chữ, số, dấu gạch ngang hoặc gạch dưới.");
        return result;
    }
    private long owner(CurrentUser actor, boolean mutate) {
        return (mutate?context.requireAdminOperator(actor):context.requireOperatorMember(actor)).getId();
    }
    private void lockOperator(long id) {
        db.queryForObject("SELECT id FROM transport_operators WHERE id=? FOR UPDATE",Long.class,id);
    }
    private Map<String,Object> voucher(long operator,long id,boolean lock) {
        var rows=db.query("SELECT * FROM operator_vouchers WHERE operator_id=? AND id=?"+(lock?" FOR UPDATE":""),(r,n)-> {
            var m=new org.springframework.jdbc.core.ColumnMapRowMapper().mapRow(r,n);
            for(String key:List.of("starts_at","ends_at","created_at","updated_at")) m.put(key,read(r,key));
            return m;
        },operator,id);
        if(rows.isEmpty()) throw new ResourceNotFoundException("VOUCHER_NOT_FOUND","Không tìm thấy mã ưu đãi.");
        return rows.get(0);
    }
    private void validate(long operator,Terms t) {
        normalize(t.code());
        if(t.name()==null || t.name().isBlank() || t.name().length()>120 || t.description()!=null && t.description().length()>500
            || !Set.of("PERCENT","FIXED_AMOUNT").contains(t.discountType()) || !positive(t.discountValue())
            || "PERCENT".equals(t.discountType()) && t.discountValue().compareTo(new BigDecimal("100"))>0
            || t.maxDiscount()!=null && !positive(t.maxDiscount()) || t.minimumSpend()==null || t.minimumSpend().signum()<0
            || t.startsAt()==null || t.endsAt()==null || !t.startsAt().isBefore(t.endsAt())
            || t.totalLimit()!=null && t.totalLimit()<1 || t.customerLimit()!=null && t.customerLimit()<1)
            throw error("VOUCHER_TERMS_INVALID","Thông tin ưu đãi không hợp lệ.");
        for(BigDecimal amount:Arrays.asList(t.discountValue(),t.maxDiscount(),t.minimumSpend()))
            if(amount!=null && (amount.stripTrailingZeros().scale()>2 || amount.precision()-amount.scale()>10))
                throw error("VOUCHER_TERMS_INVALID","Số tiền có tối đa hai chữ số thập phân.");
        if(t.operatorRouteId()!=null && db.queryForObject("SELECT COUNT(*) FROM operator_routes WHERE id=? AND operator_id=?",Long.class,t.operatorRouteId(),operator)!=1)
            throw new ResourceNotFoundException("ROUTE_NOT_FOUND","Không tìm thấy tuyến của nhà xe.");
    }
    private static boolean positive(BigDecimal value) { return value!=null && value.signum()>0; }
    public Map<String,Object> create(CurrentUser actor,Terms t) {
        long op=owner(actor,true); lockOperator(op); validate(op,t); unique(op,t.code(),-1);
        var time=parameter(now());
        db.update("""
            INSERT INTO operator_vouchers(operator_id,code,name,description,discount_type,discount_value,max_discount,minimum_spend,starts_at,ends_at,total_limit,customer_limit,operator_route_id,created_by,updated_by,created_at,updated_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,op,normalize(t.code()),t.name().strip(),t.description(),t.discountType(),t.discountValue(),t.maxDiscount(),t.minimumSpend(),parameter(utc(t.startsAt().toInstant())),parameter(utc(t.endsAt().toInstant())),t.totalLimit(),t.customerLimit(),t.operatorRouteId(),actor.id(),actor.id(),time,time);
        long id=db.queryForObject("SELECT id FROM operator_vouchers WHERE operator_id=? AND code=?",Long.class,op,normalize(t.code()));
        audit(id,actor,"CREATED"); return view(voucher(op,id,false));
    }
    private void unique(long op,String code,long except) {
        if(db.queryForObject("SELECT COUNT(*) FROM operator_vouchers WHERE operator_id=? AND code=? AND id<>?",Long.class,op,normalize(code),except)>0)
            throw error("VOUCHER_CODE_EXISTS","Nhà xe đã có mã ưu đãi này.");
    }
    public Map<String,Object> edit(CurrentUser actor,long id,Terms t) {
        long op=owner(actor,true);lockOperator(op);voucher(op,id,true);validate(op,t);
        if(!db.queryForList("SELECT id FROM voucher_usages WHERE voucher_id=? LIMIT 1 FOR UPDATE",Long.class,id).isEmpty())
            throw error("VOUCHER_TERMS_LOCKED","Ưu đãi đã được sử dụng; chỉ có thể tạm dừng hoặc kích hoạt lại.");
        unique(op,t.code(),id);
        db.update("""
            UPDATE operator_vouchers SET code=?,name=?,description=?,discount_type=?,discount_value=?,max_discount=?,minimum_spend=?,starts_at=?,ends_at=?,total_limit=?,customer_limit=?,operator_route_id=?,updated_by=?,updated_at=? WHERE id=?
            """,normalize(t.code()),t.name().strip(),t.description(),t.discountType(),t.discountValue(),t.maxDiscount(),t.minimumSpend(),parameter(utc(t.startsAt().toInstant())),parameter(utc(t.endsAt().toInstant())),t.totalLimit(),t.customerLimit(),t.operatorRouteId(),actor.id(),parameter(now()),id);
        audit(id,actor,"EDITED");return view(voucher(op,id,false));
    }
    public Map<String,Object> state(CurrentUser actor,long id,boolean active) {
        long op=owner(actor,true);lockOperator(op);var v=voucher(op,id,true);
        if(active && (!time(v,"ends_at").isAfter(now()) || exhausted(v,usage(id,true),null)))
            throw error("VOUCHER_UNAVAILABLE","Ưu đãi đã hết hạn hoặc hết lượt.");
        String state=active?"ACTIVE":"PAUSED";
        if(!state.equals(v.get("state"))) {
            db.update("UPDATE operator_vouchers SET state=?,updated_by=?,updated_at=? WHERE id=?",state,actor.id(),parameter(now()),id);
            audit(id,actor,state);
        }
        return view(voucher(op,id,false));
    }
    private void audit(long id,CurrentUser actor,String event) { db.update("INSERT INTO voucher_management_history(voucher_id,actor_id,event,created_at) VALUES(?,?,?,?)",id,actor.id(),event,parameter(now())); }
    @Transactional(readOnly=true)
    public Map<String,Object> detail(CurrentUser actor,long id) { return view(voucher(owner(actor,false),id,false)); }
    @Transactional(readOnly=true)
    public PagedResponse<Map<String,Object>> list(CurrentUser actor,String q,String state,int page,int size) {
        long op=owner(actor,false);String search="%"+(q==null?"":q.strip())+"%";
        if(state!=null && !Set.of("DRAFT","ACTIVE","PAUSED").contains(state)) throw error("VOUCHER_STATE_INVALID","Trạng thái không hợp lệ.");
        String where=" WHERE operator_id=? AND (code LIKE ? OR name LIKE ?) AND (? IS NULL OR state=?)";
        long total=db.queryForObject("SELECT COUNT(*) FROM operator_vouchers"+where,Long.class,op,search,search,state,state);
        var ids=db.queryForList("SELECT id FROM operator_vouchers"+where+" ORDER BY id DESC LIMIT ? OFFSET ?",Long.class,op,search,search,state,state,size,(long)page*size);
        return new PagedResponse<>(ids.stream().map(id->view(voucher(op,id,false))).toList(),new PagedResponse.Pagination(page,size,total,(int)((total+size-1)/size)));
    }
    private record Usage(long reserved,long redeemed,long customer) {}
    private Usage usage(long id,boolean lock) { return usage(id,lock,null); }
    private Usage usage(long id,boolean lock,Long customer) {
        // Operator serialization plus a current locking read avoids repeatable-read snapshots.
        // Aggregation bounds memory independently of campaign size; no customer history is exposed.
        return db.queryForObject("SELECT COALESCE(SUM(state='RESERVED'),0),COALESCE(SUM(state='REDEEMED'),0),COALESCE(SUM(customer_id=?),0) FROM voucher_usages WHERE voucher_id=? AND (state='REDEEMED' OR (state='RESERVED' AND expires_at>?))"+(lock?" FOR UPDATE":""),
            (r,n)->new Usage(r.getLong(1),r.getLong(2),r.getLong(3)),customer,id,parameter(now()));
    }
    private boolean exhausted(Map<String,Object> v,Usage usages,Long customer) {
        return v.get("total_limit")!=null && usages.reserved()+usages.redeemed()>=((Number)v.get("total_limit")).longValue()
            || customer!=null && v.get("customer_limit")!=null && usages.customer()>=((Number)v.get("customer_limit")).longValue();
    }
    private Map<String,Object> view(Map<String,Object> v) {
        long id=((Number)v.get("id")).longValue();var usages=usage(id,false);
        v.put("reserved",usages.reserved());
        v.put("redeemed",usages.redeemed());
        v.put("editable",db.queryForObject("SELECT COUNT(*) FROM voucher_usages WHERE voucher_id=?",Long.class,id)==0);
        v.put("effective_state",!time(v,"ends_at").isAfter(now())?"EXPIRED":exhausted(v,usages,null)?"EXHAUSTED":now().isBefore(time(v,"starts_at")) && "ACTIVE".equals(v.get("state"))?"SCHEDULED":v.get("state"));
        for(String key:List.of("starts_at","ends_at","created_at","updated_at")) v.put(key,api(time(v,key)));
        return v;
    }
    public record Application(long voucherId,Quote quote) {}
    /** Caller holds trip -> operator -> inventory. No booking/trip locks may be acquired here. */
    public Application evaluate(long operator,long route,long customer,String code,BigDecimal gross,int seats,boolean lock) {
        var ids=db.queryForList("SELECT id FROM operator_vouchers WHERE operator_id=? AND code=?"+(lock?" FOR UPDATE":""),Long.class,operator,normalize(code));
        if(ids.isEmpty()) throw error("VOUCHER_NOT_ELIGIBLE","Mã ưu đãi không áp dụng cho chuyến này.");
        var v=voucher(operator,ids.get(0),lock);var time=now();
        if(!"ACTIVE".equals(v.get("state"))) throw error("VOUCHER_INACTIVE","Mã ưu đãi chưa kích hoạt hoặc đã tạm dừng.");
        if(time.isBefore(time(v,"starts_at"))) throw error("VOUCHER_NOT_STARTED","Mã ưu đãi chưa đến thời gian sử dụng.");
        if(!time.isBefore(time(v,"ends_at"))) throw error("VOUCHER_EXPIRED","Mã ưu đãi đã hết hạn.");
        if(v.get("operator_route_id")!=null && ((Number)v.get("operator_route_id")).longValue()!=route) throw error("VOUCHER_ROUTE_MISMATCH","Mã ưu đãi không áp dụng cho tuyến này.");
        if(gross.compareTo((BigDecimal)v.get("minimum_spend"))<0) throw error("VOUCHER_MINIMUM_SPEND","Đặt vé chưa đạt giá trị tối thiểu.");
        if(exhausted(v,usage(ids.get(0),lock,customer),customer)) throw error("VOUCHER_LIMIT_REACHED","Mã ưu đãi đã hết lượt hoặc bạn đã đạt giới hạn sử dụng.");
        BigDecimal discount=PromotionMoney.discount((String)v.get("discount_type"),(BigDecimal)v.get("discount_value"),(BigDecimal)v.get("max_discount"),gross);
        if(gross.subtract(discount).compareTo(PromotionMoney.CENT.multiply(BigDecimal.valueOf(seats)))<0) throw error("VOUCHER_ZERO_PAYABLE","Ưu đãi phải để lại ít nhất 0,01 cho mỗi ghế.");
        return new Application(ids.get(0),new Quote(gross,(String)v.get("code"),(String)v.get("name"),(String)v.get("discount_type"),(BigDecimal)v.get("discount_value"),discount,gross.subtract(discount),true,null));
    }
    public void reserve(Booking b,Application app) {
        db.update("INSERT INTO voucher_usages(voucher_id,operator_id,booking_id,customer_id,state,expires_at,reserved_at) VALUES(?,?,?,?,'RESERVED',?,?)",app.voucherId(),b.getTrip().getOperatorRoute().getOperator().getId(),b.getId(),b.getCustomer().getId(),parameter(b.getPaymentDueAt()),parameter(now()));
    }
    public void finish(Booking b,boolean paid) {
        if(b.getVoucherId()==null) return;
        voucher(b.getTrip().getOperatorRoute().getOperator().getId(),b.getVoucherId(),true);
        db.update("UPDATE voucher_usages SET state=?,"+(paid?"redeemed_at":"released_at")+"=? WHERE booking_id=? AND state='RESERVED'",paid?"REDEEMED":"RELEASED",parameter(now()),b.getId());
    }
    public void deadline(Booking b) {
        if(b.getVoucherId()==null) return;
        voucher(b.getTrip().getOperatorRoute().getOperator().getId(),b.getVoucherId(),true);
        db.update("UPDATE voucher_usages SET expires_at=? WHERE booking_id=? AND state='RESERVED'",parameter(b.getPaymentDueAt()),b.getId());
    }
    public static Price price(Booking b) { return new Price(b.getGrossAmount(),b.getDiscountAmount(),b.getTotalAmount(),b.getOriginalGrossAmount(),b.getOriginalDiscountAmount(),b.getVoucherCode(),b.getVoucherName()); }
    private static LocalDateTime time(Map<String,Object> m,String key) { return (LocalDateTime)m.get(key); }
    private static BusinessException error(String code,String message) { return new BusinessException(code,message,HttpStatus.CONFLICT,null); }
}
