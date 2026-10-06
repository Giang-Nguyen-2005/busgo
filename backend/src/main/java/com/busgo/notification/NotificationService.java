package com.busgo.notification;

import static com.busgo.common.time.BusGoTime.*;
import static com.busgo.common.time.JpaJdbcTime.*;
import static com.busgo.notification.NotificationDtos.*;
import com.busgo.booking.entity.Booking;
import com.busgo.common.exception.*;
import com.busgo.common.response.PagedResponse;
import com.busgo.common.security.CurrentUser;
import com.busgo.operator.OperatorContextService;
import com.busgo.user.entity.RoleCode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class NotificationService {
    private final JdbcTemplate db;
    private final Clock clock;
    private final OperatorContextService operators;
    private final NotificationMailSender mail;
    public NotificationService(JdbcTemplate db, Clock clock, OperatorContextService operators, NotificationMailSender mail) {
        this.db=db; this.clock=clock; this.operators=operators; this.mail=mail;
    }

    /** Must join the business transaction: no visible event or delivery after rollback. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void record(Booking booking, NotificationType type, String occurrence) {
        // Same lock order as callers: trip/operator/booking. Serialize recipient occurrence creation.
        db.queryForObject("SELECT id FROM bookings WHERE id=? FOR UPDATE",Long.class,booking.getId());
        String key=booking.getId()+":"+type+":"+occurrence;
        String message=content(booking);
        if(booking.getCustomer()!=null) {
            long uid=booking.getCustomer().getId();
            create(booking,type,key,"CUSTOMER","customer:"+uid,uid,null,message,
                    booking.getContactEmail(),preferences(uid).allows(type));
        } else if(validEmail(booking.getContactEmail())) {
            create(booking,type,key,"ACCOUNTLESS","phone:"+booking.getId(),null,null,message,
                    booking.getContactEmail(),true);
        }
        if(type.operator) {
            long oid=booking.getTrip().getOperatorRoute().getOperator().getId();
            var admins=db.queryForList("""
                SELECT DISTINCT s.user_id FROM operator_staff s JOIN users u ON u.id=s.user_id
                JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id
                WHERE s.operator_id=? AND s.status='ACTIVE' AND u.status='ACTIVE' AND u.deleted_at IS NULL
                AND r.code='OPERATOR_ADMIN'
                AND (SELECT COUNT(*) FROM operator_staff s2 WHERE s2.user_id=u.id AND s2.status='ACTIVE')=1
                AND NOT EXISTS (SELECT 1 FROM user_roles x JOIN roles y ON y.id=x.role_id WHERE x.user_id=u.id AND y.code='SYSTEM_ADMIN')
                """,Long.class,oid);
            for(long uid:admins) create(booking,type,key,"OPERATOR","operator:"+oid+":"+uid,uid,oid,message,null,false);
        }
    }

    private void create(Booking b,NotificationType type,String key,String audience,String recipient,Long uid,
            Long oid,String message,String email,boolean allowed) {
        if(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM notifications WHERE recipient_key=? AND event_key=?)",Boolean.class,recipient,key))) return;
        var now=parameter(utc(clock.instant()));
        String target=audience.equals("CUSTOMER")?"/my-bookings/"+b.getId():audience.equals("OPERATOR")?"/operator/bookings/"+b.getId():null;
        db.update("INSERT INTO notifications(recipient_user_id,operator_id,audience,recipient_key,event_key,event_type,title,message,booking_id,booking_code,navigation_target,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                uid,oid,audience,recipient,key,type.name(),type.title,message,b.getId(),b.getBookingCode(),target,now);
        if(allowed && mail.configured() && validEmail(email)) {
            long id=db.queryForObject("SELECT id FROM notifications WHERE recipient_key=? AND event_key=?",Long.class,recipient,key);
            db.update("INSERT INTO notification_deliveries(notification_id,destination,next_attempt_at) VALUES(?,?,?)",id,email.trim(),now);
        }
    }

    static boolean validEmail(String email) {
        return email!=null && email.length()<=150 && email.matches("^[^\\s@\\r\\n]+@[^\\s@\\r\\n]+\\.[^\\s@\\r\\n]+$");
    }
    static String content(Booking b) {
        String time=b.getPickupTripStop().getPlannedDepartureTime().plusMinutes(b.getTrip().getDelayMinutes()).atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(BUSINESS_ZONE).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        String seats=b.getItems().stream().filter(i->!i.isCancelled()).map(i->i.getSeatCode()).reduce((a,c)->a+", "+c).orElse("Không còn ghế đang hoạt động");
        return "Mã đặt vé: "+b.getBookingCode()+"\n"+b.getTrip().getOperatorRoute().getRoute().getName()
                +"\nĐiểm đón: "+b.getPickupTripStop().getLocation().getName()+" → "+b.getDropoffTripStop().getLocation().getName()
                +"\n"+com.busgo.trip.operations.LiveTripState.label(b.getTrip().getStatus(),b.getTrip().getDelayMinutes())
                +(b.getTrip().getDelayReason()==null?"":"\nLý do: "+b.getTrip().getDelayReason())
                +(b.getTrip().getExpectedArrivalAt()==null?"":"\nDự kiến đến: "+b.getTrip().getExpectedArrivalAt().atOffset(ZoneOffset.UTC).atZoneSameInstant(BUSINESS_ZONE).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
                +"\nGiờ đón dự kiến: "+time+" (giờ Việt Nam)\nGhế hiện tại: "+seats+"\nTổng tiền hiện tại: "+b.getTotalAmount()+" VND\nTrạng thái: "
                +switch(b.getStatus()) { case CONFIRMED -> "Đã xác nhận"; case CANCELLED -> "Đã hủy"; default -> "Chờ thanh toán"; };
    }

    private record Scope(long uid,String audience,Long oid) {}
    private Scope scope(CurrentUser user,boolean operator) {
        if(operator) return new Scope(user.id(),"OPERATOR",operators.requireAdminOperator(user).getId());
        if(user==null || !user.roles().contains(RoleCode.CUSTOMER) || user.roles().contains(RoleCode.SYSTEM_ADMIN)
                || user.roles().contains(RoleCode.OPERATOR_ADMIN) || user.roles().contains(RoleCode.OPERATOR_STAFF))
            throw new BusinessException("ACCESS_DENIED","Không có quyền truy cập thông báo.",HttpStatus.FORBIDDEN,null);
        return new Scope(user.id(),"CUSTOMER",null);
    }
    private static final String OWN="recipient_user_id=? AND audience=? AND operator_id <=> ?";
    @Transactional(readOnly=true)
    public PagedResponse<Notification> list(CurrentUser user,boolean operator,int page,int size) {
        var s=scope(user,operator);
        if(page<0 || size<1 || size>100) throw new BusinessException("INVALID_REQUEST","Phân trang không hợp lệ.",HttpStatus.BAD_REQUEST,null);
        long count=db.queryForObject("SELECT COUNT(*) FROM notifications WHERE "+OWN,Long.class,s.uid,s.audience,s.oid);
        var rows=db.query("SELECT * FROM notifications WHERE "+OWN+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
            (r,n)->new Notification(r.getLong("id"),NotificationType.valueOf(r.getString("event_type")),r.getString("title"),r.getString("message"),r.getString("booking_code"),r.getString("navigation_target"),api(read(r,"created_at")),api(read(r,"read_at"))),s.uid,s.audience,s.oid,size,(long)page*size);
        return new PagedResponse<>(rows,new PagedResponse.Pagination(page,size,count,(int)((count+size-1)/size)));
    }
    @Transactional(readOnly=true)
    public long unread(CurrentUser user,boolean operator) {
        var s=scope(user,operator);
        return db.queryForObject("SELECT COUNT(*) FROM notifications WHERE "+OWN+" AND read_at IS NULL",Long.class,s.uid,s.audience,s.oid);
    }
    @Transactional
    public void mark(CurrentUser user,boolean operator,Long id) {
        var s=scope(user,operator);
        if(id!=null) own(s,id);
        db.update("UPDATE notifications SET read_at=COALESCE(read_at,?) WHERE "+OWN+(id==null?"":" AND id="+id),parameter(utc(clock.instant())),s.uid,s.audience,s.oid);
    }
    private void own(Scope s,long id) {
        if(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM notifications WHERE "+OWN+" AND id=?)",Boolean.class,s.uid,s.audience,s.oid,id)))
            throw new ResourceNotFoundException("NOTIFICATION_NOT_FOUND","Không tìm thấy thông báo.");
    }
    public Preferences preferences(long uid) {
        return db.query("SELECT * FROM notification_preferences WHERE user_id=?",(r,n)->new Preferences(r.getBoolean("booking_payment_email"),r.getBoolean("booking_change_email"),r.getBoolean("trip_reminder_email")),uid)
                .stream().findFirst().orElse(new Preferences(true,true,true));
    }
    @Transactional(readOnly=true)
    public Preferences preferences(CurrentUser user) { scope(user,false); return preferences(user.id()); }
    @Transactional
    public Preferences savePreferences(CurrentUser user,Preferences p) {
        scope(user,false);
        db.update("INSERT INTO notification_preferences(user_id,booking_payment_email,booking_change_email,trip_reminder_email) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE booking_payment_email=VALUES(booking_payment_email),booking_change_email=VALUES(booking_change_email),trip_reminder_email=VALUES(trip_reminder_email)",user.id(),p.bookingPaymentEmail(),p.bookingChangeEmail(),p.tripReminderEmail());
        return p;
    }
    @Transactional(readOnly=true)
    public List<Delivery> deliveries(CurrentUser user,boolean operator,long id) {
        own(scope(user,operator),id);
        return db.query("SELECT * FROM notification_deliveries WHERE notification_id=? ORDER BY id LIMIT 10",(r,n)->new Delivery(r.getLong("id"),r.getString("status"),r.getInt("attempt_count"),api(read(r,"last_attempt_at")),api(read(r,"next_attempt_at")),api(read(r,"sent_at")),r.getString("error_summary")),id);
    }
    @Transactional
    public void retry(CurrentUser user,boolean operator,long id) {
        own(scope(user,operator),id);
        db.update("UPDATE notification_deliveries SET next_attempt_at=? WHERE notification_id=? AND status='FAILED' AND attempt_count<5",parameter(utc(clock.instant())),id);
    }
}
