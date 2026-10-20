package com.busgo.support;

import static com.busgo.support.SupportDtos.*;
import com.busgo.audit.AuditService;
import com.busgo.common.exception.*;
import com.busgo.common.response.PagedResponse;
import com.busgo.common.security.CurrentUser;
import com.busgo.operator.OperatorContextService;
import com.busgo.user.entity.RoleCode;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class SupportService {
    private final JdbcTemplate db;
    private final OperatorContextService operators;
    private final AuditService audit;
    private final Clock clock;
    public SupportService(JdbcTemplate db,OperatorContextService operators,AuditService audit,Clock clock) {
        this.db=db;this.operators=operators;this.audit=audit;this.clock=clock;
    }
    private record Scope(String role,long user,Long operator) { boolean admin(){return role.equals("OPERATOR_ADMIN");} }
    private Scope scope(CurrentUser u,String area) {
        if(u==null) throw denied();
        if(area.equals("admin")) {
            if(!u.roles().contains(RoleCode.SYSTEM_ADMIN)) throw denied();
            return new Scope("SYSTEM_ADMIN",u.id(),null);
        }
        if(area.equals("operator")) return new Scope(u.roles().contains(RoleCode.OPERATOR_ADMIN)?"OPERATOR_ADMIN":"OPERATOR_STAFF",u.id(),operators.requireOperatorMember(u).getId());
        if(!u.roles().equals(List.of(RoleCode.CUSTOMER))) throw denied();
        return new Scope("CUSTOMER",u.id(),null);
    }
    private static BusinessException denied() { return new BusinessException("ACCESS_DENIED","Không có quyền xử lý yêu cầu này.",HttpStatus.FORBIDDEN,null); }
    private static BusinessException invalid(String message) { return new BusinessException("INVALID_SUPPORT_ACTION",message,HttpStatus.CONFLICT,null); }
    private static ResourceNotFoundException missing() { return new ResourceNotFoundException("SUPPORT_NOT_FOUND","Không tìm thấy yêu cầu hỗ trợ."); }
    private Timestamp now() { return Timestamp.from(clock.instant()); }
    private static Instant instant(ResultSet r,String c) throws SQLException { var t=r.getTimestamp(c);return t==null?null:t.toInstant(); }
    private static Long nullable(ResultSet r,String c) throws SQLException { long n=r.getLong(c);return r.wasNull()?null:n; }
    private static String text(String s) { if(s==null || s.isBlank() || s.length()>4000) throw invalid("Nội dung phải có từ 1 đến 4000 ký tự.");return s.trim(); }
    private static String hash(String s) { return com.busgo.auth.AuthService.hash(s); }
    private static String fingerprint(Object... values) {
        var canonical=new StringBuilder();
        for(Object value:values) {String part=String.valueOf(value);canonical.append(part.length()).append(':').append(part);}
        return hash(canonical.toString());
    }
    private static final String SELECT="SELECT t.*,b.booking_code,o.name operator_name,(SELECT d.status FROM support_disputes d WHERE d.ticket_id=t.id) dispute_status FROM support_tickets t JOIN bookings b ON b.id=t.booking_id JOIN transport_operators o ON o.id=t.operator_id ";
    private Ticket map(ResultSet r,int n) throws SQLException {
        return new Ticket(r.getLong("id"),r.getLong("booking_id"),r.getString("booking_code"),r.getLong("customer_id"),r.getLong("operator_id"),r.getString("operator_name"),Category.valueOf(r.getString("category")),r.getString("subject"),State.valueOf(r.getString("status")),Escalation.valueOf(r.getString("escalation")),Priority.valueOf(r.getString("priority")),nullable(r,"assigned_user_id"),r.getLong("version"),r.getInt("reopen_count"),instant(r,"resolved_at"),r.getBoolean("resolved_by_admin"),instant(r,"created_at"),instant(r,"updated_at"),r.getString("dispute_status"),List.of());
    }
    private String own(Scope s) { return switch(s.role) {case "CUSTOMER" -> "t.customer_id="+s.user;case "SYSTEM_ADMIN" -> "EXISTS(SELECT 1 FROM support_disputes d WHERE d.ticket_id=t.id)";default -> "t.operator_id="+s.operator;}; }
    private Ticket ticket(Scope s,long id,boolean lock) {
        // Only support rows are locked. Never acquire commerce locks from this workspace.
        if(lock) {
            var ids=db.queryForList("SELECT t.id FROM support_tickets t WHERE t.id=? AND "+own(s)+" FOR UPDATE",Long.class,id);
            if(ids.isEmpty()) throw missing();
        }
        return db.query(SELECT+"WHERE t.id=? AND "+own(s),this::map,id).stream().findFirst().orElseThrow(SupportService::missing);
    }
    private boolean recent(Ticket t) { return t.resolvedAt()!=null && !clock.instant().isAfter(t.resolvedAt().plus(Duration.ofDays(7))); }
    private List<String> actions(Scope s,Ticket t) {
        var a=new ArrayList<String>();boolean active=t.status()!=State.CLOSED && t.status()!=State.RESOLVED;
        if(s.role.equals("SYSTEM_ADMIN")) {
            String status=db.queryForObject("SELECT status FROM support_disputes WHERE ticket_id=?",String.class,t.id());
            if(status.equals("SUBMITTED") || status.equals("WAITING_INFORMATION")) a.add("review");
            if(status.equals("UNDER_REVIEW")) a.addAll(List.of("request-information","decision"));
            if(!status.equals("CLOSED")) a.add("messages");return a;
        }
        if(s.role.equals("CUSTOMER")) {
            if(active) a.add("messages");
            if(t.status()==State.RESOLVED && t.escalation()!=Escalation.PLATFORM) {
                a.add("accept-resolution");
                if(recent(t)) {
                    if(t.reopenCount()<2) a.add("reopen");
                    if(!t.resolvedByAdmin() || !platformUsed(t.id())) a.add("appeal");
                }
            }return a;
        }
        if(active) {a.add("notes");if(s.admin() || t.category().sensitive() || Objects.equals(t.assignedUserId(),s.user)) a.add("messages");}
        if(t.escalation()==Escalation.PLATFORM) return a;
        if(s.admin() && active) {
            a.addAll(List.of("assign","priority","transition","resolve"));
            if(!platformUsed(t.id())) a.add("escalate");
        }
        if(s.role.equals("OPERATOR_STAFF") && active && !t.category().sensitive() && t.escalation()==Escalation.NONE) {
            if(t.assignedUserId()==null) a.add("claim");
            if(Objects.equals(t.assignedUserId(),s.user)) a.addAll(List.of("transition","resolve","escalate"));
        }
        return a;
    }
    private boolean platformUsed(long ticketId) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM support_disputes WHERE ticket_id=?)",Boolean.class,ticketId));
    }
    private Ticket view(Scope s,Ticket t) {return new Ticket(t.id(),t.bookingId(),t.bookingCode(),t.customerId(),t.operatorId(),t.operatorName(),t.category(),t.subject(),t.status(),t.escalation(),t.priority(),t.assignedUserId(),t.version(),t.reopenCount(),t.resolvedAt(),t.resolvedByAdmin(),t.createdAt(),t.updatedAt(),t.disputeStatus(),actions(s,t));}
    @Transactional(readOnly=true)
    public Ticket detail(CurrentUser u,String area,long id) {var s=scope(u,area);return view(s,ticket(s,id,false));}
    private static void paging(int page,int size) {if(page<0 || page>100000 || size<1 || size>100) throw invalid("Phân trang không hợp lệ.");}
    private <T> PagedResponse<T> paged(List<T> rows,long count,int page,int size) {return new PagedResponse<>(rows,new PagedResponse.Pagination(page,size,count,(int)((count+size-1)/size)));}
    @Transactional(readOnly=true)
    public PagedResponse<Ticket> list(CurrentUser u,String area,State status,String queue,int page,int size) {
        return list(u,area,status,queue,null,page,size);
    }
    @Transactional(readOnly=true)
    public PagedResponse<Ticket> list(CurrentUser u,String area,State status,String queue,String disputeStatus,int page,int size) {
        paging(page,size);var s=scope(u,area);String where=" WHERE "+own(s);var args=new ArrayList<Object>();
        if(disputeStatus!=null && !disputeStatus.isBlank()) {
            if(!area.equals("admin") || !Set.of("SUBMITTED","UNDER_REVIEW","WAITING_INFORMATION","CLOSED").contains(disputeStatus)) throw invalid("Bộ lọc tranh chấp không hợp lệ.");
            where+=" AND EXISTS(SELECT 1 FROM support_disputes d WHERE d.ticket_id=t.id AND d.status=?)";args.add(disputeStatus);
        }
        if(status!=null) {where+=" AND t.status=?";args.add(status.name());}
        if(queue!=null && !queue.isBlank()) {
            if(s.operator==null || !Set.of("mine","unassigned").contains(queue)) throw invalid("Bộ lọc không hợp lệ.");
            where+=queue.equals("mine")?" AND t.assigned_user_id="+s.user:" AND t.assigned_user_id IS NULL";
        }
        long count=db.queryForObject("SELECT COUNT(*) FROM support_tickets t"+where,Long.class,args.toArray());
        args.add(size);args.add((long)page*size);
        return paged(db.query(SELECT+where+" ORDER BY t.created_at DESC,t.id DESC LIMIT ? OFFSET ?",this::map,args.toArray()),count,page,size);
    }
    public Ticket create(CurrentUser u,Create c) {
        var s=scope(u,"customer");String fingerprint=fingerprint(c.bookingId(),c.category(),c.subject().trim(),text(c.description()));
        var replay=creationReplay(s,c.requestKey(),fingerprint);
        if(replay!=null) return replay;
        var bookings=db.queryForList("SELECT r.operator_id FROM bookings b JOIN trips t ON t.id=b.trip_id JOIN operator_routes r ON r.id=t.operator_route_id WHERE b.id=? AND b.customer_id=? AND b.source='WEB'",c.bookingId(),u.id());
        if(bookings.isEmpty()) throw missing();
        long oid=((Number)bookings.get(0).get("operator_id")).longValue();
        try {
            // The unique customer/request-key index serializes racing inserts without
            // taking a user X lock ahead of commerce's booking/notification FK locks.
            db.update("INSERT INTO support_tickets(booking_id,customer_id,operator_id,category,subject,escalation,request_key,request_hash,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",c.bookingId(),u.id(),oid,c.category().name(),c.subject().trim(),c.category().sensitive()?"OPERATOR_ADMIN":"NONE",c.requestKey(),fingerprint,now(),now());
        } catch(org.springframework.dao.DuplicateKeyException e) {
            var committed=creationReplay(s,c.requestKey(),fingerprint);
            if(committed!=null) return committed;
            throw e;
        }
        long id=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);var t=ticket(s,id,false);
        activity(s,t,"CREATED","PUBLIC",text(c.description()),t.status().name(),t.status().name());audit.record(u,oid,"SUPPORT",id,"CREATED");notify(t,s,"SUPPORT_TICKET_CREATED");return view(s,t);
    }
    private Ticket creationReplay(Scope s,String key,String fingerprint) {
        var existing=db.queryForList("SELECT id,request_hash FROM support_tickets WHERE customer_id=? AND request_key=?",s.user,key);
        if(existing.isEmpty()) return null;
        if(!existing.get(0).get("request_hash").equals(fingerprint)) throw invalid("Mã gửi đã dùng cho nội dung khác.");
        return view(s,ticket(s,((Number)existing.get(0).get("id")).longValue(),false));
    }
    private void activity(Scope s,Ticket t,String type,String visibility,String content,String from,String to) {
        db.update("INSERT INTO support_activity(ticket_id,actor_id,actor_role,event_type,visibility,content,from_status,to_status,assigned_user_id,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)",t.id(),s.user,s.role,type,visibility,content,from,to,t.assignedUserId(),now());
    }
    @Transactional(readOnly=true)
    public PagedResponse<Activity> messages(CurrentUser u,String area,long id,int page,int size) {
        paging(page,size);var s=scope(u,area);ticket(s,id,false);
        String filter=s.role.equals("CUSTOMER")?" AND visibility='PUBLIC'":s.role.equals("SYSTEM_ADMIN")?"":" AND visibility<>'PLATFORM'";
        long count=db.queryForObject("SELECT COUNT(*) FROM support_activity WHERE ticket_id=?"+filter,Long.class,id);
        return paged(db.query("SELECT * FROM support_activity WHERE ticket_id=?"+filter+" ORDER BY created_at,id LIMIT ? OFFSET ?",(r,n)->new Activity(r.getLong("id"),r.getLong("actor_id"),r.getString("actor_role"),r.getString("event_type"),r.getString("visibility"),r.getString("content"),r.getString("from_status"),r.getString("to_status"),nullable(r,"assigned_user_id"),instant(r,"created_at")),id,size,(long)page*size),count,page,size);
    }
    private void eligibleStaff(long oid,long uid) {
        long count=db.queryForObject("""
            SELECT COUNT(*) FROM operator_staff s JOIN users u ON u.id=s.user_id
            WHERE s.operator_id=? AND s.user_id=? AND s.status='ACTIVE' AND u.status='ACTIVE' AND u.deleted_at IS NULL
            AND (SELECT COUNT(*) FROM operator_staff x WHERE x.user_id=u.id AND x.status='ACTIVE')=1
            AND EXISTS(SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.code='OPERATOR_STAFF')
            AND NOT EXISTS(SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.code='SYSTEM_ADMIN')
            """,Long.class,oid,uid);
        if(count!=1) throw denied();
    }
    public Ticket command(CurrentUser u,String area,long id,String action,Command c) {
        var s=scope(u,area);var t=ticket(s,id,true);String content=text(c.content());
        String fp=fingerprint(action,content,c.internal(),c.assignedUserId(),c.status(),c.priority(),c.outcome());
        var replay=db.queryForList("SELECT request_hash FROM support_requests WHERE ticket_id=? AND actor_id=? AND request_key=?",id,u.id(),c.requestKey());
        if(!replay.isEmpty()) {if(!replay.get(0).get("request_hash").equals(fp)) throw invalid("Mã gửi đã dùng cho nội dung khác.");return view(s,t);}
        String permission=action.equals("messages") && c.internal()?"notes":action;
        if(!actions(s,t).contains(permission)) throw denied();
        if(c.version()!=t.version()) throw invalid("Yêu cầu đã thay đổi. Vui lòng tải lại.");
        State next=t.status();String visibility=c.internal()?"INTERNAL":"PUBLIC";String event=action.toUpperCase(Locale.ROOT).replace('-','_');
        if(c.internal() && !Set.of("OPERATOR_ADMIN","OPERATOR_STAFF").contains(s.role)) throw denied();
        if(!action.equals("messages") && c.internal()) throw invalid("Thao tác này phải hiển thị cho khách hàng.");
        switch(action) {
            case "messages" -> { if(!c.internal() && s.role.equals("CUSTOMER") && t.status()==State.WAITING_CUSTOMER) next=State.IN_PROGRESS; }
            case "claim" -> {eligibleStaff(t.operatorId(),u.id());db.update("UPDATE support_tickets SET assigned_user_id=? WHERE id=?",u.id(),id);next=State.IN_PROGRESS;}
            case "assign" -> {
                if(c.assignedUserId()!=null) {eligibleStaff(t.operatorId(),c.assignedUserId());if(t.category().sensitive() || t.escalation()!=Escalation.NONE) throw invalid("Yêu cầu này do quản trị viên xử lý.");}
                db.update("UPDATE support_tickets SET assigned_user_id=? WHERE id=?",c.assignedUserId(),id);
            }
            case "priority" -> {if(c.priority()==null) throw invalid("Chọn mức ưu tiên.");db.update("UPDATE support_tickets SET priority=? WHERE id=?",c.priority().name(),id);}
            case "transition" -> {if(c.status()!=State.IN_PROGRESS && c.status()!=State.WAITING_CUSTOMER) throw invalid("Trạng thái không hợp lệ.");if(c.status()==t.status()) throw invalid("Trạng thái không thay đổi.");next=c.status();}
            case "resolve" -> {next=State.RESOLVED;db.update("UPDATE support_tickets SET resolved_at=?,resolved_by=?,resolved_by_admin=? WHERE id=?",now(),u.id(),s.admin(),id);}
            case "accept-resolution" -> next=State.CLOSED;
            case "reopen" -> {next=State.OPEN;db.update("UPDATE support_tickets SET reopen_count=reopen_count+1,resolved_at=NULL,resolved_by=NULL,resolved_by_admin=FALSE WHERE id=?",id);}
            case "appeal","escalate" -> {
                boolean platform=s.admin() || (s.role.equals("CUSTOMER") && t.resolvedByAdmin());
                if(platform) {
                    if(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM support_disputes WHERE ticket_id=?)",Boolean.class,id))) throw invalid("Yêu cầu đã được nền tảng xem xét.");
                    db.update("INSERT INTO support_disputes(ticket_id,reason,created_at) VALUES(?,?,?)",id,content,now());
                }
                db.update("UPDATE support_tickets SET escalation=?,assigned_user_id=NULL,resolved_at=NULL,resolved_by_admin=FALSE WHERE id=?",platform?"PLATFORM":"OPERATOR_ADMIN",id);next=State.IN_PROGRESS;
            }
            case "review" -> db.update("UPDATE support_disputes SET status='UNDER_REVIEW' WHERE ticket_id=?",id);
            case "request-information" -> db.update("UPDATE support_disputes SET status='WAITING_INFORMATION' WHERE ticket_id=?",id);
            case "decision" -> {
                if(c.outcome()==null) throw invalid("Chọn kết quả xem xét.");
                event="DECISION_"+c.outcome().name();
                db.update("UPDATE support_disputes SET status='CLOSED',outcome=?,decision=?,decided_by=?,decided_at=? WHERE ticket_id=?",c.outcome().name(),content,u.id(),now(),id);
                if(c.outcome()==Outcome.ADDITIONAL_REVIEW_REQUIRED) {db.update("UPDATE support_tickets SET escalation='OPERATOR_ADMIN',assigned_user_id=NULL WHERE id=?",id);next=State.IN_PROGRESS;}
                else next=State.CLOSED;
                /* Structured outcome remains on dispute; content stays within 4000 characters. */
            }
            default -> throw invalid("Thao tác không hợp lệ.");
        }
        db.update("UPDATE support_tickets SET status=?,version=version+1,updated_at=? WHERE id=?",next.name(),now(),id);
        var updated=ticket(s,id,false);activity(s,updated,event,visibility,content,t.status().name(),next.name());
        audit.record(u,t.operatorId(),"SUPPORT",id,event);
        db.update("INSERT INTO support_requests(ticket_id,actor_id,request_key,request_hash) VALUES(?,?,?,?)",id,u.id(),c.requestKey(),fp);
        if(!c.internal()) notify(updated,s,switch(action) {
            case "decision" -> "DISPUTE_DECISION_RECORDED";
            case "messages" -> s.role.equals("CUSTOMER")?"SUPPORT_CUSTOMER_REPLIED":"SUPPORT_OPERATOR_REPLIED";
            case "resolve" -> "SUPPORT_TICKET_RESOLVED";
            case "claim","assign" -> "SUPPORT_TICKET_ASSIGNED";
            case "appeal","escalate" -> "SUPPORT_TICKET_ESCALATED";
            default -> "SUPPORT_TICKET_UPDATED";
        });
        return view(s,updated);
    }
    private void notify(Ticket t,Scope actor,String type) {
        // No conversation text in notifications; recipients reauthorize when opening the case.
        var recipients=new LinkedHashMap<Long,String>();
        if(!actor.role.equals("CUSTOMER")) recipients.put(t.customerId(),"CUSTOMER");
        db.queryForList("""
            SELECT DISTINCT s.user_id FROM operator_staff s JOIN users u ON u.id=s.user_id
            WHERE s.operator_id=? AND s.status='ACTIVE' AND u.status='ACTIVE' AND u.deleted_at IS NULL
            AND (SELECT COUNT(*) FROM operator_staff x WHERE x.user_id=u.id AND x.status='ACTIVE')=1
            AND EXISTS(SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.code='OPERATOR_ADMIN')
            AND NOT EXISTS(SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.code='SYSTEM_ADMIN')
            """,Long.class,t.operatorId()).forEach(uid->recipients.put(uid,"OPERATOR"));
        if(t.assignedUserId()!=null) { try { eligibleStaff(t.operatorId(),t.assignedUserId());recipients.put(t.assignedUserId(),"OPERATOR"); } catch(BusinessException ignored) { /* Removed staff never receive new work. */ } }
        if(t.escalation()==Escalation.PLATFORM) db.queryForList("SELECT DISTINCT u.id FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id WHERE r.code='SYSTEM_ADMIN' AND u.status='ACTIVE' AND u.deleted_at IS NULL",Long.class).forEach(uid->recipients.put(uid,"PLATFORM"));
        recipients.remove(actor.user);
        for(var e:recipients.entrySet()) {
            String area=e.getValue();String target=area.equals("CUSTOMER")?"/support/tickets/":area.equals("OPERATOR")?"/operator/support/tickets/":"/admin/disputes/";
            // PLATFORM uses a support-specific audience; inbox access is narrow and role-aware.
            db.update("INSERT INTO support_notifications(recipient_user_id,operator_id,audience,recipient_key,event_key,event_type,title,message,booking_id,booking_code,navigation_target,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",e.getKey(),area.equals("OPERATOR")?t.operatorId():null,area,"support:"+e.getKey(),"support:"+t.id()+":"+t.version(),type,"Yêu cầu hỗ trợ #"+t.id(),"Yêu cầu hỗ trợ có cập nhật mới. Mở yêu cầu để xem chi tiết.",t.bookingId(),t.bookingCode(),target+t.id(),now());
        }
    }
    @Transactional(readOnly=true)
    public Dispute dispute(CurrentUser u,long id) {
        var s=scope(u,"admin");var t=ticket(s,id,false);
        return db.query("SELECT * FROM support_disputes WHERE ticket_id=?",(r,n)->new Dispute(r.getLong("id"),view(s,t),r.getString("reason"),r.getString("status"),r.getString("outcome")==null?null:Outcome.valueOf(r.getString("outcome")),r.getString("decision"),nullable(r,"decided_by"),instant(r,"decided_at")),id).get(0);
    }
    @Transactional(readOnly=true)
    public List<Assignee> assignees(CurrentUser u) {
        var s=scope(u,"operator");if(!s.admin()) throw denied();
        return db.query("SELECT s.user_id,u.full_name FROM operator_staff s JOIN users u ON u.id=s.user_id WHERE s.operator_id=? AND s.status='ACTIVE' AND u.status='ACTIVE' AND u.deleted_at IS NULL ORDER BY u.full_name,u.id LIMIT 100",(r,n)->new Assignee(r.getLong(1),r.getString(2)),s.operator).stream().filter(a->{try{eligibleStaff(s.operator,a.id());return true;}catch(BusinessException e){return false;}}).toList();
    }
    @Transactional(readOnly=true)
    public List<Counter> dashboard(CurrentUser u,String area) {
        var s=scope(u,area);var result=new ArrayList<Counter>();
        if(area.equals("admin")) {
            var counts=db.query("SELECT status,COUNT(*) n FROM support_disputes GROUP BY status ORDER BY status",(r,n)->new Counter(r.getString(1),r.getLong(2)));
            return List.of("SUBMITTED","UNDER_REVIEW","WAITING_INFORMATION","CLOSED").stream()
                .map(state->new Counter(state,counts.stream().filter(c->c.key().equals(state)).mapToLong(Counter::count).sum())).toList();
        }
        String own=own(s);if(s.role.equals("OPERATOR_STAFF")) own+=" AND t.assigned_user_id="+s.user;
        var counts=db.query("SELECT status,COUNT(*) n FROM support_tickets t WHERE "+own+" GROUP BY status ORDER BY status",(r,n)->new Counter(r.getString(1),r.getLong(2)));
        for(State state:State.values()) result.add(new Counter(state.name(),counts.stream().filter(c->c.key().equals(state.name())).mapToLong(Counter::count).sum()));
        if(s.admin()) {
            for(String key:List.of("UNASSIGNED","OPERATOR_ADMIN","PLATFORM","OVERDUE")) {
                String filter=switch(key){case "UNASSIGNED"->"assigned_user_id IS NULL";case "OVERDUE"->"updated_at < ?";default->"escalation='"+key+"'";};
                Object[] args=key.equals("OVERDUE")?new Object[]{Timestamp.from(clock.instant().minus(Duration.ofHours(24)))}:new Object[]{};
                result.add(new Counter(key,db.queryForObject("SELECT COUNT(*) FROM support_tickets t WHERE "+own+" AND status IN ('OPEN','IN_PROGRESS') AND "+filter,Long.class,args)));
            }
        }else if(s.operator!=null) {
            result.add(new Counter("AVAILABLE",db.queryForObject("SELECT COUNT(*) FROM support_tickets t WHERE t.operator_id=? AND assigned_user_id IS NULL AND escalation='NONE' AND status IN ('OPEN','IN_PROGRESS')",Long.class,s.operator)));
            result.add(new Counter("RESOLVED_BY_ME",db.queryForObject("SELECT COUNT(*) FROM support_tickets t WHERE t.operator_id=? AND resolved_by=? AND status IN ('RESOLVED','CLOSED')",Long.class,s.operator,s.user)));
        }
        return result;
    }
    @Transactional(readOnly=true)
    public PagedResponse<Notice> inbox(CurrentUser u,String area,int page,int size) {
        paging(page,size);var s=scope(u,area);String audience=s.operator!=null?"OPERATOR":s.role.equals("SYSTEM_ADMIN")?"PLATFORM":"CUSTOMER";
        String where=" WHERE recipient_user_id=? AND audience=? AND operator_id <=> ?";
        long count=db.queryForObject("SELECT COUNT(*) FROM support_notifications"+where,Long.class,s.user,audience,s.operator);
        return paged(db.query("SELECT * FROM support_notifications"+where+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",(r,n)->new Notice(r.getLong("id"),r.getString("title"),r.getString("navigation_target"),instant(r,"created_at"),instant(r,"read_at")),s.user,audience,s.operator,size,(long)page*size),count,page,size);
    }
    public void markInbox(CurrentUser u,String area) {
        var s=scope(u,area);String audience=s.operator!=null?"OPERATOR":s.role.equals("SYSTEM_ADMIN")?"PLATFORM":"CUSTOMER";
        db.update("UPDATE support_notifications SET read_at=COALESCE(read_at,?) WHERE recipient_user_id=? AND audience=? AND operator_id <=> ?",now(),s.user,audience,s.operator);
    }
    @Transactional(readOnly=true)
    public PagedResponse<AuditEvent> audits(CurrentUser u,String area,String resource,Long resourceId,int page,int size) {
        paging(page,size);var s=scope(u,area);if(area.equals("operator") && !s.admin()) throw denied();
        String where=" WHERE 1=1";var args=new ArrayList<Object>();
        if(s.operator!=null){where+=" AND operator_id=?";args.add(s.operator);}
        if(resource!=null){if(!Set.of("SUPPORT","GOVERNANCE","VOUCHER").contains(resource)) throw invalid("Loại sự kiện không hợp lệ.");where+=" AND resource_type=?";args.add(resource);}
        if(resourceId!=null){where+=" AND resource_id=?";args.add(resourceId);}
        long count=db.queryForObject("SELECT COUNT(*) FROM audit_events"+where,Long.class,args.toArray());args.add(size);args.add((long)page*size);
        return paged(db.query("SELECT * FROM audit_events"+where+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",(r,n)->new AuditEvent(r.getLong("id"),r.getString("event_code"),r.getLong("actor_id"),r.getString("actor_role"),nullable(r,"operator_id"),r.getString("resource_type"),r.getLong("resource_id"),r.getString("action"),r.getString("outcome"),instant(r,"created_at")),args.toArray()),count,page,size);
    }
}
