package com.busgo.booking;

import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import static com.busgo.common.time.BusGoTime.utc;
import static com.busgo.common.time.JpaJdbcTime.parameter;

@Component
public class ModificationExpiryJob {
    private final JdbcTemplate db;
    private final ModificationService service;
    private final Clock clock;
    public ModificationExpiryJob(JdbcTemplate db,ModificationService service,Clock clock) {
        this.db=db; this.service=service; this.clock=clock;
    }
    @Scheduled(fixedDelayString="${busgo.booking.modification-expiry-interval:PT1M}",initialDelayString="${busgo.booking.modification-expiry-initial-delay:PT1M}")
    public void expire() {
        for(long id:db.queryForList("SELECT id FROM booking_modifications WHERE status IN ('HELD','AWAITING_PAYMENT') AND expires_at<=? ORDER BY expires_at,id LIMIT 100",Long.class,parameter(utc(clock.instant())))) {
            try { service.expire(id); }
            catch(RuntimeException e) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Modification expiry failed for {}",id,e); }
        }
    }
}
