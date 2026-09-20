package com.busgo.hold;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "busgo.booking")
public record SeatHoldProperties(Duration seatHoldDuration, Integer maxSeatsPerHold) {
    public SeatHoldProperties {
        if (seatHoldDuration == null) seatHoldDuration = Duration.ofMinutes(10);
        if (maxSeatsPerHold == null) maxSeatsPerHold = 5;
        if (seatHoldDuration.isZero() || seatHoldDuration.isNegative()) {
            throw new IllegalArgumentException("Seat hold duration must be positive.");
        }
        if (maxSeatsPerHold < 1) {
            throw new IllegalArgumentException("Maximum seats per hold must be positive.");
        }
    }
}
