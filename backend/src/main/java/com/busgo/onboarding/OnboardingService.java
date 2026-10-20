package com.busgo.onboarding;

import static com.busgo.onboarding.OnboardingDtos.*;
import com.busgo.auth.*;
import com.busgo.common.exception.*;
import com.busgo.common.response.PagedResponse;
import com.busgo.common.security.CurrentUser;
import com.busgo.user.entity.RoleCode;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class OnboardingService {
    private final JdbcTemplate db;
    private final com.busgo.audit.AuditService audit;
    private final Clock clock;
    private final PasswordEncoder passwords;
    private final com.busgo.notification.NotificationService notifications;
    private static final SecureRandom RANDOM=new SecureRandom();
    public OnboardingService(JdbcTemplate db,Clock clock,PasswordEncoder passwords,com.busgo.notification.NotificationService notifications,com.busgo.audit.AuditService audit) {
        this.audit=audit;
        this.db=db;this.clock=clock;this.passwords=passwords;this.notifications=notifications;
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC); }
    private Object jpaNow() { return com.busgo.common.time.JpaJdbcTime.parameter(now()); }
    private long inserted() { return db.queryForObject("SELECT LAST_INSERT_ID()",Long.class); }
    private static Map<String,Object> apiRow(Map<String,Object> row) {
        row.replaceAll((key,value)-> {
            if(value instanceof LocalDateTime t) return t.atOffset(ZoneOffset.UTC);
            if(value instanceof List<?> list) return list.stream().map(item->{
                if(item instanceof Map<?,?> map) {
                    Map<String,Object> copy=new LinkedHashMap<>();map.forEach((k,v)->copy.put(k.toString(),v));return apiRow(copy);
                }
                return item;
            }).toList();
            return value;
        });return row;
    }
    private static BusinessException conflict(String code,String message) { return new BusinessException(code,message,HttpStatus.CONFLICT,null); }
    private static BusinessException invalidInvite() { return conflict("INVALID_INVITATION","Lời mời không hợp lệ hoặc đã hết hạn."); }
    private void admin(CurrentUser u) {
        if(u==null || !u.roles().equals(List.of(RoleCode.SYSTEM_ADMIN)))
            throw new BusinessException("ACCESS_DENIED","Chỉ quản trị hệ thống được thực hiện thao tác này.",HttpStatus.FORBIDDEN,null);
    }
    private void emailAvailable(String email) {
        if(db.queryForObject("SELECT COUNT(*) FROM users WHERE email=?",Long.class,email)>0)
            throw conflict("DEDICATED_ADMIN_EMAIL_REQUIRED","Vui lòng sử dụng email riêng chưa có tài khoản BusGo cho quản trị nhà xe.");
    }
    private Map<String,Object> application(long id,boolean lock) {
        var rows=db.queryForList("SELECT id,name,legal_name,legal_code,email,phone,address,contact_name,admin_email,website,public_description,status,operator_id,submitted_at FROM operator_applications WHERE id=?"+(lock?" FOR UPDATE":""),id);
        if(rows.isEmpty()) throw new ResourceNotFoundException("APPLICATION_NOT_FOUND","Không tìm thấy đơn đăng ký.");
        return rows.get(0);
    }
    private void history(Long aid,Long oid,String event,CurrentUser actor,String category,String old,String state,String note) {
        db.update("INSERT INTO operator_onboarding_history(application_id,operator_id,event_type,actor_category,actor_id,old_state,new_state,note,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
            aid,oid,event,category,actor==null?null:actor.id(),old,state,note,now());
        if(actor!=null) audit.record(actor,oid,"GOVERNANCE",oid==null?aid:oid,event);
    }
    public Map<String,Object> submit(ApplicationRequest r) {
        if(db.queryForObject("SELECT COUNT(*) FROM transport_operators WHERE legal_code=?",Long.class,r.legalCode())>0) throw duplicate();
        try {
            db.update("INSERT INTO operator_applications(name,legal_name,legal_code,email,phone,address,contact_name,admin_email,website,public_description,status,submitted_at) VALUES(?,?,?,?,?,?,?,?,?,?,'SUBMITTED',?)",
                r.name(),r.legalName()==null || r.legalName().isBlank()?r.name():r.legalName(),r.legalCode(),r.email(),r.phone(),r.address(),r.contactName(),r.adminEmail(),r.website(),r.publicDescription(),now());
        } catch(DuplicateKeyException e) { throw duplicate(); }
        long id=inserted();history(id,null,"APPLICATION_SUBMITTED",null,"APPLICANT",null,"SUBMITTED",null);
        notifications.onboarding(id,r.email(),false,null);
        return Map.of("id",id,"status","SUBMITTED");
    }
    private static BusinessException duplicate() { return conflict("LEGAL_IDENTITY_ALREADY_EXISTS","Mã đăng ký đã có đơn đang xử lý hoặc nhà xe được phê duyệt."); }
    @Transactional(readOnly=true)
    public PagedResponse<Map<String,Object>> applications(CurrentUser u,ApplicationStatus status,String q,int page,int size) {
        admin(u);String pattern="%"+(q==null?"":q.strip())+"%";
        String where=" FROM operator_applications WHERE (? IS NULL OR status=?) AND (name LIKE ? OR legal_code LIKE ?)";
        String state=status==null?null:status.name();
        long total=db.queryForObject("SELECT COUNT(*)"+where,Long.class,state,state,pattern,pattern);
        var rows=db.queryForList("SELECT id,name,legal_name,legal_code,status,submitted_at,operator_id"+where+" ORDER BY id DESC LIMIT ? OFFSET ?",state,state,pattern,pattern,size,(long)page*size);
        return new PagedResponse<>(rows.stream().map(OnboardingService::apiRow).toList(),new PagedResponse.Pagination(page,size,total,(int)Math.ceil(total/(double)size)));
    }
    @Transactional(readOnly=true)
    public Map<String,Object> detail(CurrentUser u,long id) {
        admin(u);var result=application(id,false);
        result.put("invitations",db.queryForList("SELECT id,intended_email,created_at,expires_at,accepted_at,revoked_at FROM operator_admin_invitations WHERE application_id=? ORDER BY id DESC LIMIT 50",id));
        result.put("history",db.queryForList("SELECT * FROM operator_onboarding_history WHERE application_id=? ORDER BY id DESC LIMIT 100",id));
        return apiRow(result);
    }
    public Map<String,Object> review(CurrentUser u,long id,Decision r) {
        admin(u);var a=application(id,true);requireState(a,"SUBMITTED");
        db.update("UPDATE operator_applications SET status='UNDER_REVIEW' WHERE id=?",id);
        history(id,null,"APPLICATION_UNDER_REVIEW",u,"SYSTEM_ADMIN","SUBMITTED","UNDER_REVIEW",r.note());return detail(u,id);
    }
    public Map<String,Object> reject(CurrentUser u,long id,Reason r) {
        admin(u);var a=application(id,true);String old=(String)a.get("status");
        if(!Set.of("SUBMITTED","UNDER_REVIEW").contains(old)) throw state();
        requireReason(r.reason());db.update("UPDATE operator_applications SET status='REJECTED' WHERE id=?",id);
        history(id,null,"APPLICATION_REJECTED",u,"SYSTEM_ADMIN",old,"REJECTED",r.reason().strip());
        notifications.onboarding(id,(String)a.get("email"),true,r.reason().strip());return detail(u,id);
    }
    public InviteResult approve(CurrentUser u,long id,Decision r) {
        admin(u);var a=application(id,true);requireState(a,"UNDER_REVIEW");emailAvailable((String)a.get("admin_email"));
        try {
            db.update("INSERT INTO transport_operators(name,code,phone,email,address,status,legal_name,legal_code,legal_contact,website,public_description,created_at,updated_at) VALUES(?,?,?,?,?,'INACTIVE',?,?,?,?,?,?,?)",
                a.get("name"),"ONB-"+id,a.get("phone"),a.get("email"),a.get("address"),a.get("legal_name"),a.get("legal_code"),a.get("contact_name"),a.get("website"),a.get("public_description"),jpaNow(),jpaNow());
        } catch(DuplicateKeyException e) { throw duplicate(); }
        long oid=inserted();db.update("UPDATE operator_applications SET status='APPROVED',operator_id=? WHERE id=?",oid,id);
        history(id,oid,"APPLICATION_APPROVED",u,"SYSTEM_ADMIN","UNDER_REVIEW","APPROVED",r.note());
        return invite(u,id,oid,(String)a.get("admin_email"),false);
    }
    private InviteResult invite(CurrentUser u,long aid,long oid,String email,boolean reissue) {
        byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiry=clock.instant().plus(Duration.ofHours(72));
        db.update("INSERT INTO operator_admin_invitations(application_id,operator_id,intended_email,token_hash,created_at,expires_at) VALUES(?,?,?,?,?,?)",aid,oid,email,AuthService.hash(token),now(),LocalDateTime.ofInstant(expiry,ZoneOffset.UTC));
        history(aid,oid,reissue?"ADMIN_INVITE_REISSUED":"ADMIN_INVITE_CREATED",u,"SYSTEM_ADMIN",null,"PENDING",null);
        // Fragment keeps the secret out of frontend HTTP access logs and Referer headers.
        return new InviteResult(aid,oid,"/operator-invitation#"+token,expiry.atOffset(ZoneOffset.UTC));
    }
    public InviteResult reissue(CurrentUser u,long id) {
        admin(u);var a=application(id,true);requireState(a,"APPROVED");long oid=((Number)a.get("operator_id")).longValue();
        var o=operator(oid,true);if(o.get("onboarded_at")!=null || !"INACTIVE".equals(o.get("status"))) throw state();
        emailAvailable((String)a.get("admin_email"));
        db.update("UPDATE operator_admin_invitations SET revoked_at=? WHERE application_id=? AND accepted_at IS NULL AND revoked_at IS NULL",now(),id);
        return invite(u,id,oid,(String)a.get("admin_email"),true);
    }
    private Map<String,Object> token(String token) {
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalidInvite();
        var rows=db.queryForList("SELECT * FROM operator_admin_invitations WHERE token_hash=?",AuthService.hash(token));
        if(rows.isEmpty()) throw invalidInvite();return rows.get(0);
    }
    private void valid(Map<String,Object> i) {
        if(i.get("accepted_at")!=null || i.get("revoked_at")!=null || !((LocalDateTime)i.get("expires_at")).isAfter(now())) throw invalidInvite();
    }
    @Transactional(readOnly=true)
    public Map<String,Object> context(String token) {
        var i=token(token);valid(i);var o=operator(((Number)i.get("operator_id")).longValue(),false);
        return Map.of("operatorName",o.get("name"),"email",i.get("intended_email"),"expiresAt",((LocalDateTime)i.get("expires_at")).atOffset(ZoneOffset.UTC));
    }
    public Map<String,Object> accept(String token,Acceptance r) {
        PasswordPolicy.validate(r.password());if(!Objects.equals(r.password(),r.confirmPassword())) throw conflict("PASSWORD_CONFIRMATION","Mật khẩu xác nhận chưa khớp.");
        var initial=token(token);long aid=((Number)initial.get("application_id")).longValue();
        var a=application(aid,true);requireState(a,"APPROVED");long oid=((Number)a.get("operator_id")).longValue();
        var o=operator(oid,true);
        var i=db.queryForMap("SELECT * FROM operator_admin_invitations WHERE id=? FOR UPDATE",initial.get("id"));valid(i);
        if(o.get("onboarded_at")!=null || !"INACTIVE".equals(o.get("status"))) throw invalidInvite();
        String email=(String)i.get("intended_email");emailAvailable(email);
        try {
            db.update("INSERT INTO users(full_name,email,phone,password_hash,status,created_at,updated_at) VALUES(?,?,?,?,'ACTIVE',?,?)",r.fullName().strip(),email,a.get("phone"),passwords.encode(r.password()),jpaNow(),jpaNow());
        } catch(DuplicateKeyException e) { throw conflict("DEDICATED_ADMIN_EMAIL_REQUIRED","Vui lòng sử dụng email riêng chưa có tài khoản BusGo cho quản trị nhà xe."); }
        long uid=inserted();db.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code='OPERATOR_ADMIN'",uid);
        db.update("INSERT INTO operator_staff(operator_id,user_id,staff_code,status,created_at) VALUES(?,?,'ADMIN-1','ACTIVE',?)",oid,uid,jpaNow());
        db.update("UPDATE operator_admin_invitations SET accepted_at=? WHERE id=?",now(),i.get("id"));
        db.update("UPDATE transport_operators SET status='ACTIVE',onboarded_at=?,updated_at=? WHERE id=?",now(),jpaNow(),oid);
        var actor=new CurrentUser(uid,List.of(RoleCode.OPERATOR_ADMIN));
        history(aid,oid,"ADMIN_INVITE_ACCEPTED",actor,"INVITEE","PENDING","ACCEPTED",null);
        history(aid,oid,"OPERATOR_ACTIVATED",actor,"INVITEE","INACTIVE","ACTIVE",null);
        return Map.of("operatorId",oid,"status","ACTIVE");
    }
    private Map<String,Object> operator(long id,boolean lock) {
        var rows=db.queryForList("SELECT id,name,status,legal_name,legal_code,legal_contact,website,public_description,onboarded_at FROM transport_operators WHERE id=?"+(lock?" FOR UPDATE":""),id);
        if(rows.isEmpty()) throw new ResourceNotFoundException("OPERATOR_NOT_FOUND","Không tìm thấy nhà xe.");return rows.get(0);
    }
    public Map<String,Object> status(CurrentUser u,long oid,Reason r,boolean suspend) {
        admin(u);requireReason(r.reason());var o=operator(oid,true);String old=suspend?"ACTIVE":"SUSPENDED",next=suspend?"SUSPENDED":"ACTIVE";
        if(!old.equals(o.get("status"))) throw state();
        if(!suspend && (o.get("onboarded_at")==null || validAdmins(oid)==0)) throw conflict("OPERATOR_ACTIVATION_NOT_ALLOWED","Nhà xe phải hoàn tất đăng ký và có quản trị viên hợp lệ.");
        db.update("UPDATE transport_operators SET status=?,updated_at=? WHERE id=?",next,jpaNow(),oid);
        history(null,oid,suspend?"OPERATOR_SUSPENDED":"OPERATOR_REACTIVATED",u,"SYSTEM_ADMIN",old,next,r.reason().strip());return governance(u,oid);
    }
    private long validAdmins(long oid) {
        return db.queryForObject("""
            SELECT COUNT(*) FROM operator_staff s JOIN users u ON u.id=s.user_id
            WHERE s.operator_id=? AND s.status='ACTIVE' AND u.status='ACTIVE' AND u.deleted_at IS NULL
            AND EXISTS(SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.code='OPERATOR_ADMIN')
            AND (SELECT COUNT(*) FROM user_roles ur WHERE ur.user_id=u.id)=1
            AND (SELECT COUNT(*) FROM operator_staff x WHERE x.user_id=u.id AND x.status='ACTIVE')=1
            """,Long.class,oid);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> governance(CurrentUser u,long oid) {
        admin(u);var o=operator(oid,false);o.put("activeAdminCount",validAdmins(oid));
        o.put("applications",db.queryForList("SELECT id,status,submitted_at FROM operator_applications WHERE operator_id=?",oid));
        o.put("invitations",db.queryForList("SELECT id,intended_email,created_at,expires_at,accepted_at,revoked_at FROM operator_admin_invitations WHERE operator_id=? ORDER BY id DESC LIMIT 50",oid));
        o.put("history",db.queryForList("SELECT * FROM operator_onboarding_history WHERE operator_id=? ORDER BY id DESC LIMIT 100",oid));
        o.put("activeRoutes",db.queryForObject("SELECT COUNT(*) FROM operator_routes WHERE operator_id=? AND status='ACTIVE'",Long.class,oid));
        o.put("upcomingTrips",db.queryForObject("SELECT COUNT(*) FROM trips t JOIN operator_routes r ON r.id=t.operator_route_id WHERE r.operator_id=? AND t.status='SCHEDULED' AND t.departure_time>?",Long.class,oid,jpaNow()));return apiRow(o);
    }
    @Transactional(readOnly=true)
    public Map<String,Long> dashboard(CurrentUser u) {
        admin(u);Map<String,Long> r=new LinkedHashMap<>();
        for(String s:List.of("SUBMITTED","UNDER_REVIEW")) r.put(s,db.queryForObject("SELECT COUNT(*) FROM operator_applications WHERE status=?",Long.class,s));
        r.put("ONBOARDING",db.queryForObject("SELECT COUNT(*) FROM operator_applications a JOIN transport_operators o ON o.id=a.operator_id WHERE a.status='APPROVED' AND o.onboarded_at IS NULL",Long.class));
        for(String s:List.of("ACTIVE","SUSPENDED")) r.put(s,db.queryForObject("SELECT COUNT(*) FROM transport_operators WHERE status=?",Long.class,s));
        r.put("ACTIVE_ROUTES",db.queryForObject("SELECT COUNT(*) FROM operator_routes r JOIN transport_operators o ON o.id=r.operator_id WHERE r.status='ACTIVE' AND o.status='ACTIVE'",Long.class));
        r.put("UPCOMING_TRIPS",db.queryForObject("SELECT COUNT(*) FROM trips WHERE status='SCHEDULED' AND departure_time>?",Long.class,jpaNow()));return r;
    }
    private static void requireState(Map<String,Object> a,String expected) { if(!expected.equals(a.get("status"))) throw state(); }
    private static BusinessException state() { return conflict("INVALID_ONBOARDING_STATE","Trạng thái đã thay đổi hoặc không cho phép thao tác này."); }
    private static void requireReason(String reason) { if(reason==null || reason.isBlank() || reason.length()>1000) throw conflict("REASON_REQUIRED","Vui lòng nhập lý do."); }
}
