package com.busgo.audit;

import com.busgo.common.security.CurrentUser;
import java.time.Clock;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Explicit allowlisted metadata only; never pass request bodies or exception text. */
@Service
public class AuditService {
    private final JdbcTemplate db;
    private final Clock clock;
    public AuditService(JdbcTemplate db, Clock clock) { this.db=db; this.clock=clock; }
    @Transactional(propagation=Propagation.MANDATORY)
    public void record(CurrentUser actor, Long operator, String resource, long id, String action) {
        db.update("INSERT INTO audit_events(event_code,actor_id,actor_role,operator_id,resource_type,resource_id,action,metadata,created_at) VALUES(?,?,?,?,?,?,?,JSON_OBJECT(),?)",
            resource+"_"+action,actor.id(),actor.roles().stream().map(Enum::name).sorted().reduce((a,b)->a+","+b).orElse(""),operator,resource,id,action,Timestamp.from(clock.instant()));
    }
}
