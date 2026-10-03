package com.busgo.operations;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public final class OperationsDtos {
    private OperationsDtos() {}
    public enum Capability { DRIVER, ATTENDANT }
    public enum EmployeeStatus { ACTIVE, INACTIVE }
    public enum Attendance { EXPECTED, CHECKED_IN, BOARDED, NO_SHOW }
    public record EmployeeInput(@NotBlank @Size(max=50) String employeeCode,
            @NotBlank @Size(max=100) String fullName, @NotBlank @Size(max=20) String phone,
            @NotNull EmployeeStatus status, @NotEmpty Set<Capability> capabilities,
            @Size(max=50) String licenceNumber, @Size(max=30) String licenceClass,
            LocalDate licenceExpiryDate, @PositiveOrZero Long version) {}
    public record Employee(Long id, String employeeCode, String fullName, String phone,
            EmployeeStatus status, Set<Capability> capabilities, String licenceNumber,
            String licenceClass, LocalDate licenceExpiryDate, long version) {}
    public record CrewInput(@NotNull @Positive Long employeeId, @NotNull Capability duty) {}
    public record CrewReplacement(@NotNull @Size(max=20) List<@jakarta.validation.Valid CrewInput> assignments) {}
    public record PickupContext(@NotNull @Positive Long stopId, @Size(max=500) String reason) {}
}
