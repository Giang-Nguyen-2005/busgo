package com.busgo.fleet;

import java.time.*;
import com.busgo.fleet.MaintenanceDtos.Status;

/** Pure, deterministic rules shared by writes and readiness. */
public final class MaintenanceRules {
    private MaintenanceRules() {}
    public static boolean overlaps(LocalDateTime start, LocalDateTime end, LocalDateTime otherStart, LocalDateTime otherEnd) {
        return start.isBefore(otherEnd) && otherStart.isBefore(end);
    }
    public static boolean allowed(Status from, Status to) {
        return from == Status.SCHEDULED && (to == Status.IN_PROGRESS || to == Status.CANCELLED)
                || from == Status.IN_PROGRESS && to == Status.COMPLETED;
    }
    public static boolean overdue(LocalDate due, LocalDate today) { return due != null && due.isBefore(today); }
    public static boolean dueSoon(LocalDate due, LocalDate today) {
        return due != null && !due.isBefore(today) && !due.isAfter(today.plusDays(7));
    }
    public static boolean ready(com.busgo.fleet.entity.BusStatus status, boolean activeType,
            boolean activeMaintenance, boolean plannedNow) {
        return status==com.busgo.fleet.entity.BusStatus.AVAILABLE && activeType && !activeMaintenance && !plannedNow;
    }
}
