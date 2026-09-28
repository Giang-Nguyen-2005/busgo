package com.busgo.operator;

import com.busgo.common.exception.BusinessException;
import com.busgo.common.security.CurrentUser;
import com.busgo.operator.entity.TransportOperator;
import com.busgo.operator.repository.OperatorStaffRepository;
import com.busgo.user.entity.RoleCode;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperatorContextService {
    private final OperatorStaffRepository staff;

    public OperatorContextService(OperatorStaffRepository staff) {
        this.staff = staff;
    }

    @PreAuthorize("hasRole('OPERATOR_ADMIN')")
    @Transactional(readOnly = true)
    public TransportOperator requireAdminOperator(CurrentUser user) {
        if (user == null || user.roles().contains(RoleCode.SYSTEM_ADMIN)
                || !user.roles().contains(RoleCode.OPERATOR_ADMIN)) {
            throw denied();
        }
        var memberships = staff.findActiveByUserId(user.id());
        if (memberships.size() != 1) {
            throw denied();
        }
        return memberships.get(0).getOperator();
    }

    @PreAuthorize("hasAnyRole('OPERATOR_STAFF','OPERATOR_ADMIN')")
    @Transactional(readOnly = true)
    public TransportOperator requireOperatorMember(CurrentUser user) {
        if (user == null || user.roles().contains(RoleCode.SYSTEM_ADMIN)
                || (!user.roles().contains(RoleCode.OPERATOR_STAFF)
                && !user.roles().contains(RoleCode.OPERATOR_ADMIN))) {
            throw denied();
        }
        var memberships = staff.findActiveByUserId(user.id());
        if (memberships.size() != 1) throw denied();
        return memberships.get(0).getOperator();
    }

    private static BusinessException denied() {
        return new BusinessException("ACCESS_DENIED", "An active operator membership is required.",
                HttpStatus.FORBIDDEN, null);
    }
}
