package com.busgo.admin;

import com.busgo.operator.entity.OperatorStatus;
import com.busgo.operator.OperatorStaffDtos.OperatorStaffResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;

public final class AdminOperatorDtos {
    private AdminOperatorDtos() {}
    public record StaffCounts(long total, long active, long admins, long staff) {}
    public record OperationalCounts(long buses, long routes, long trips, long bookings) {}
    public record AdminOperatorListItem(Long id, String code, String name, String phone,
            String email, OperatorStatus status, long activeStaffCount, long activeAdminCount,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    public record AdminOperatorDetail(Long id, String code, String name, String phone,
            String email, String address, OperatorStatus status, StaffCounts staffCounts,
            OperationalCounts operationalCounts, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    public record CreateInitialOperatorAdminRequest(
            @NotBlank @Size(max=100) String fullName,
            @NotBlank @Email @Size(max=150) String email,
            @NotBlank @Size(max=20) String phone,
            String password,
            @NotBlank @Size(max=50) String staffCode) {}
    public record CreateOperatorRequest(
            @NotBlank @Size(max=150) String name,
            @NotBlank @Size(max=50) @Pattern(regexp="[A-Za-z0-9_-]+") String code,
            @Size(max=20) String phone, @Email @Size(max=150) String email,
            @Size(max=255) String address, @NotNull OperatorStatus status,
            @NotNull @Valid CreateInitialOperatorAdminRequest initialAdmin) {}
    public record UpdateOperatorRequest(
            @Size(min=1,max=150) @Pattern(regexp="(?s).*\\S.*") String name,
            @Size(max=20) String phone, @Email @Size(max=150) String email,
            @Size(max=255) String address) {}
    public record UpdateOperatorStatusRequest(@NotNull OperatorStatus status) {}
}
