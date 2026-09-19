package com.busgo.common.time;

import java.time.*;
import java.time.temporal.ChronoUnit;

public final class BusGoTime {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private BusGoTime() {}

    public static UtcWindow businessDate(LocalDate date) {
        return new UtcWindow(utc(date.atStartOfDay(BUSINESS_ZONE)),
                utc(date.plusDays(1).atStartOfDay(BUSINESS_ZONE)));
    }

    public static LocalDateTime businessTime(LocalDate date, LocalTime time) {
        return utc(date.atTime(time).atZone(BUSINESS_ZONE));
    }

    public static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    public static LocalDateTime utc(ZonedDateTime value) {
        return utc(value.toInstant());
    }

    public static OffsetDateTime api(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    public record UtcWindow(LocalDateTime startInclusive, LocalDateTime endExclusive) {}
}
