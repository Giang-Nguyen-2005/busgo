package com.busgo.trip.operations;

import static com.busgo.common.time.BusGoTime.api;
import com.busgo.trip.entity.*;
import java.time.*;

/** Public current state. No actor IDs or history are exposed. */
public record LiveTripState(TripStatus lifecycle, int delayMinutes, String delayReason,
        OffsetDateTime scheduledDepartureAt, OffsetDateTime scheduledArrivalAt,
        OffsetDateTime expectedDepartureAt, OffsetDateTime expectedArrivalAt,
        OffsetDateTime selectedPickupScheduledAt, OffsetDateTime selectedPickupEstimatedAt,
        OffsetDateTime actualDepartureAt, OffsetDateTime actualArrivalAt,
        OffsetDateTime operationalUpdatedAt, String label) {
    public static LiveTripState of(Trip t, LocalDateTime pickup) {
        return new LiveTripState(t.getStatus(), t.getDelayMinutes(), t.getDelayReason(),
                api(t.getDepartureTime()), api(t.getEstimatedArrivalTime()),
                api(t.getExpectedDepartureAt()==null?t.getDepartureTime():t.getExpectedDepartureAt()),
                api(t.getExpectedArrivalAt()==null?t.getEstimatedArrivalTime():t.getExpectedArrivalAt()),
                api(pickup), api(pickup==null?null:pickup.plusMinutes(t.getDelayMinutes())),
                api(t.getActualDepartureAt()), api(t.getActualArrivalAt()), api(t.getOperationalUpdatedAt()),
                label(t.getStatus(),t.getDelayMinutes()));
    }
    public static String label(TripStatus status,int delay) {
        String late="Trễ "+delay+" phút";
        return switch(status) {
            case SCHEDULED -> delay>0?late:"Đúng giờ";
            case BOARDING -> "Đang lên xe"+(delay>0?" · "+late:"");
            case DEPARTED -> delay>0?"Đang di chuyển · "+late:"Đã khởi hành";
            case COMPLETED -> "Đã đến";
            case CANCELLED -> "Đã hủy chuyến";
        };
    }
}
