package com.busgo.hold;

import static com.busgo.common.time.BusGoTime.utc;

import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SeatHoldCleanupJob {
    private final SeatHoldInventoryRepository inventory;
    private final Clock clock;

    public SeatHoldCleanupJob(SeatHoldInventoryRepository inventory, Clock clock) {
        this.inventory = inventory;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${busgo.booking.seat-hold-cleanup-interval:PT1M}",
            initialDelayString = "${busgo.booking.seat-hold-cleanup-initial-delay:PT1M}")
    @Transactional
    public int releaseExpired() {
        return releaseExpiredAt(utc(clock.instant()));
    }

    @Transactional
    public int releaseExpiredAt(java.time.LocalDateTime cutoff) {
        return inventory.releaseExpired(cutoff);
    }
}
