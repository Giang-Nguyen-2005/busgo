package com.busgo.notification;

import static com.busgo.common.time.BusGoTime.utc;
import static com.busgo.common.time.JpaJdbcTime.parameter;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class NotificationDeliveryWorker {
    private final JdbcTemplate db;
    private final NotificationMailSender mail;
    private final NotificationService notifications;
    private final Clock clock;
    private final TransactionTemplate transaction;
    public NotificationDeliveryWorker(JdbcTemplate db,NotificationMailSender mail,NotificationService notifications,
            Clock clock,PlatformTransactionManager manager) {
        this.db=db; this.mail=mail; this.notifications=notifications; this.clock=clock;
        transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    @Scheduled(fixedDelayString="${busgo.notifications.delivery-interval:PT1M}",initialDelayString="${busgo.notifications.initial-delay:PT1M}")
    public void run() {
        if(!mail.configured()) return;
        // One row per independent transaction; concurrent workers skip each other's row locks.
        for(int i=0;i<20;i++) {
            try { if(!Boolean.TRUE.equals(transaction.execute(status->deliverOne()))) break; }
            catch(org.springframework.dao.DataAccessException failure) { break; }
        }
    }
    private boolean deliverOne() {
        var rows=db.queryForList("""
            SELECT id,notification_id,destination,attempt_count FROM notification_deliveries
            WHERE status IN ('PENDING','FAILED') AND attempt_count<5 AND next_attempt_at<=?
            ORDER BY next_attempt_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
            """,parameter(utc(clock.instant())));
        if(rows.isEmpty()) return false;
        var d=rows.get(0); long id=((Number)d.get("id")).longValue();
        var n=db.queryForMap("SELECT * FROM notifications WHERE id=?",d.get("notification_id"));
        var type=NotificationType.valueOf((String)n.get("event_type"));
        if(n.get("recipient_user_id")!=null && !notifications.preferences(((Number)n.get("recipient_user_id")).longValue()).allows(type)) {
            db.update("UPDATE notification_deliveries SET status='SKIPPED',error_summary='Email preference disabled' WHERE id=?",id);
            return true;
        }
        var now=utc(clock.instant()); int attempt=((Number)d.get("attempt_count")).intValue()+1;
        try {
            mail.send((String)d.get("destination"),(String)n.get("title"),(String)n.get("message"));
        } catch(RuntimeException failure) {
            // Never persist SMTP exception text: it may include credentials, host details or PII.
            db.update("UPDATE notification_deliveries SET status='FAILED',attempt_count=?,last_attempt_at=?,next_attempt_at=?,error_summary='Email delivery failed' WHERE id=?",
                    attempt,parameter(now),parameter(now.plusMinutes(Math.min(60,1L<<(attempt-1)))),id);
            return true;
        }
        db.update("UPDATE notification_deliveries SET status='SENT',attempt_count=?,last_attempt_at=?,sent_at=?,error_summary=NULL WHERE id=?",attempt,parameter(now),parameter(utc(clock.instant())),id);
        return true;
    }
}
