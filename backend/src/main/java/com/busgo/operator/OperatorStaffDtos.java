package com.busgo.operator;

import com.busgo.common.entity.ActiveStatus;
import com.busgo.user.entity.*;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;

public final class OperatorStaffDtos {
    private OperatorStaffDtos() {}

    public enum OperatorRole { OPERATOR_ADMIN, OPERATOR_STAFF }

    public record StaffUser(Long id, String fullName, String email, String phone, UserStatus status) {}
    public record OperatorStaffResponse(Long staffId, String staffCode,
            ActiveStatus membershipStatus, OperatorRole role, StaffUser user,
            OffsetDateTime createdAt) {}

    public record CreateOperatorStaffRequest(
            @NotBlank @Size(max=100) String fullName,
            @NotBlank @Email @Size(max=150) String email,
            @NotBlank @Size(max=20) String phone,
            String password,
            @NotBlank @Size(max=50) String staffCode,
            @NotNull OperatorRole role) {}

    public record UpdateOperatorStaffRequest(
            @Size(min=1,max=50) @Pattern(regexp="(?s).*\\S.*") String staffCode,
            ActiveStatus status, OperatorRole role) {}
}
