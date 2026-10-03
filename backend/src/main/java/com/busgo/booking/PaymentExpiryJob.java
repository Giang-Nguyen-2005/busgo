package com.busgo.booking;

import static com.busgo.common.time.BusGoTime.utc;
import static com.busgo.common.time.JpaJdbcTime.parameter;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentExpiryJob {
    private static final Logger log=LoggerFactory.getLogger(PaymentExpiryJob.class);
    private final CancellationService cancellations;
    private final JdbcTemplate db;
    private final Clock clock;
    private final int batchSize;
    public PaymentExpiryJob(CancellationService cancellations,JdbcTemplate db,Clock clock,
            @Value("${busgo.booking.payment-expiry-batch-size:100}") int batchSize) {
        if(batchSize<1 || batchSize>1000) throw new IllegalArgumentException("Expiry batch must be 1..1000");
        this.cancellations=cancellations; this.db=db; this.clock=clock; this.batchSize=batchSize;
    }
    @Scheduled(fixedDelayString="${busgo.booking.payment-expiry-interval:PT1M}",initialDelayString="${busgo.booking.payment-expiry-initial-delay:PT1M}")
    public int expirePending() {
        var ids=db.queryForList("SELECT id FROM bookings WHERE status='PENDING' AND payment_due_at IS NOT NULL AND payment_due_at<=? AND payment_method<>'PAY_ON_BOARD' ORDER BY payment_due_at,id LIMIT ?",Long.class,parameter(utc(clock.instant())),batchSize);
        int expired=0;
        for(long id:ids) {
            try { if(cancellations.expire(id)) expired++; }
            catch(RuntimeException e) { log.warn("Payment expiry rejected inconsistent booking {}: {}",id,e.getMessage()); }
        }
        return expired;
    }
}
