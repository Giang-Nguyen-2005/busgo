package com.busgo.fleet;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;
import com.busgo.fleet.entity.BusStatus;

public final class MaintenanceDtos {
    private MaintenanceDtos() {}
    public enum Type { PERIODIC_SERVICE, OIL_CHANGE, TIRE, BRAKE, ELECTRICAL, ENGINE, AIR_CONDITIONING, INSPECTION, REPAIR, OTHER }
    public enum Status { SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED }
    public record Schedule(@NotNull Type maintenanceType, @NotBlank @Size(max=150) String title,
            @Size(max=1000) String note, @NotNull OffsetDateTime scheduledStart,
            @NotNull OffsetDateTime scheduledEnd) {}
    public record Complete(@PositiveOrZero Long odometerKm, LocalDate nextDueDate,
            @PositiveOrZero Long nextDueOdometerKm, @Size(max=1000) String note) {}
    public record Cancel(@Size(max=500) String reason) {}
    public record Maintenance(long id, long busId, String licensePlate, Type maintenanceType,
            Status status, String title, String note, OffsetDateTime scheduledStart,
            OffsetDateTime scheduledEnd, OffsetDateTime startedAt, OffsetDateTime completedAt,
            OffsetDateTime cancelledAt, Long odometerKm, LocalDate nextDueDate,
            Long nextDueOdometerKm, String completionNote, String cancellationReason,
            long createdBy, Long completedBy, Long cancelledBy, OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}
    public record AssignedTrip(long id, String route, OffsetDateTime departureTime,
            OffsetDateTime estimatedArrivalTime, String status) {}
    public record Readiness(boolean operationalReady, BusStatus status, String maintenanceState,
            Long activeMaintenanceId, OffsetDateTime nextMaintenanceDate, Type nextMaintenanceType,
            LocalDate nextDueDate, AssignedTrip nextAssignedTrip, List<String> warnings) {}
    public record StatusHistory(long id, BusStatus previousStatus, BusStatus newStatus,
            String reasonCode, Long maintenanceId, long changedBy, OffsetDateTime changedAt) {}
    public record FleetWarnings(long availableBuses, long maintenanceBuses, long inactiveBuses,
            long maintenanceDueSoon, long overdueMaintenance, List<AssignedTrip> upcomingNotReady) {}
}
