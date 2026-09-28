package com.busgo.operator;

import static com.busgo.operator.OperatorStaffDtos.*;
import static com.busgo.common.time.BusGoTime.api;

import com.busgo.auth.PasswordPolicy;
import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.exception.*;
import com.busgo.common.response.PagedResponse;
import com.busgo.common.security.CurrentUser;
import com.busgo.operator.entity.*;
import com.busgo.operator.repository.*;
import com.busgo.user.entity.*;
import com.busgo.user.repository.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OperatorStaffService {
    private static final java.util.concurrent.ConcurrentHashMap<Long,Object> OPERATOR_MUTATION_LOCKS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final OperatorContextService context;
    private final TransportOperatorRepository operators;
    private final OperatorStaffRepository staff;
    private final OperatorStaffQueryRepository queries;
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final PasswordEncoder passwords;
    private final TransactionTemplate transactions;

    public OperatorStaffService(OperatorContextService context, TransportOperatorRepository operators,
            OperatorStaffRepository staff, OperatorStaffQueryRepository queries,
            UserRepository users, RoleRepository roles, UserRoleRepository userRoles,
            PasswordEncoder passwords, PlatformTransactionManager transactionManager) {
        this.context=context; this.operators=operators; this.staff=staff; this.queries=queries;
        this.users=users; this.roles=roles; this.userRoles=userRoles; this.passwords=passwords;
        this.transactions=new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly=true)
    public PagedResponse<OperatorStaffResponse> list(CurrentUser current, String q,
            ActiveStatus status, OperatorRole role, int page, int size) {
        Long operatorId=context.requireAdminOperator(current).getId();
        var result=queries.search(operatorId,q,status,role,page,size);
        int pages=(int)Math.ceil(result.total()/(double)size);
        return new PagedResponse<>(result.rows(),new PagedResponse.Pagination(page,size,result.total(),pages));
    }

    @Transactional
    public OperatorStaffResponse create(CurrentUser current, CreateOperatorStaffRequest request) {
        TransportOperator operator=lockedOperator(context.requireAdminOperator(current).getId());
        queries.lockOperator(operator.getId());
        PasswordPolicy.validate(request.password());
        String email=email(request.email());
        String staffCode=staffCode(request.staffCode());
        if (users.existsByEmail(email)) throw duplicateEmail();
        if (staff.existsByOperatorIdAndStaffCodeIgnoreCase(operator.getId(),staffCode)) throw duplicateStaffCode();
        User user=new User(); user.setFullName(request.fullName().strip()); user.setEmail(email);
        user.setPhone(request.phone().strip()); user.setPasswordHash(passwords.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE);
        try { users.saveAndFlush(user); }
        catch (DataIntegrityViolationException ex) { throw duplicateEmail(); }
        userRoles.saveAndFlush(new UserRole(user,role(request.role())));
        OperatorStaff membership=new OperatorStaff(); membership.setOperator(operator); membership.setUser(user);
        membership.setStaffCode(staffCode); membership.setStatus(ActiveStatus.ACTIVE);
        try { return response(staff.saveAndFlush(membership),request.role()); }
        catch (DataIntegrityViolationException ex) { throw duplicateStaffCode(); }
    }

    public OperatorStaffResponse update(CurrentUser current, Long staffId,
            UpdateOperatorStaffRequest request) {
        if (request.staffCode()==null && request.status()==null && request.role()==null)
            throw validation("At least one staff field is required.");
        Long operatorId=context.requireAdminOperator(current).getId();
        synchronized (OPERATOR_MUTATION_LOCKS.computeIfAbsent(operatorId, ignored -> new Object())) {
            return transactions.execute(status -> updateLocked(current, operatorId, staffId, request));
        }
    }

    private OperatorStaffResponse updateLocked(CurrentUser current, Long operatorId, Long staffId,
            UpdateOperatorStaffRequest request) {
        if (!context.requireAdminOperator(current).getId().equals(operatorId)) throw notFound();
        lockedOperator(operatorId); queries.lockOperator(operatorId);
        OperatorStaff membership=staff.findByIdAndOperatorId(staffId,operatorId).orElseThrow(OperatorStaffService::notFound);
        OperatorRole existing=operatorRole(membership.getUser().getId());
        ActiveStatus targetStatus=request.status()==null?membership.getStatus():request.status();
        OperatorRole targetRole=request.role()==null?existing:request.role();
        if (targetStatus==ActiveStatus.ACTIVE && staff.hasOtherActiveMembership(membership.getUser().getId(),operatorId))
            throw membershipConflict();
        if (existing==OperatorRole.OPERATOR_ADMIN && (targetRole!=OperatorRole.OPERATOR_ADMIN
                || targetStatus!=ActiveStatus.ACTIVE) && queries.lockActiveAdminIds(operatorId).size()<=1)
            throw lastAdmin();
        if (request.staffCode()!=null) {
            String code=staffCode(request.staffCode());
            if (staff.existsByOperatorIdAndStaffCodeIgnoreCaseAndIdNot(operatorId,code,staffId)) throw duplicateStaffCode();
            membership.setStaffCode(code);
        }
        membership.setStatus(targetStatus);
        if (targetRole!=existing) {
            userRoles.deleteByUserIdAndRoleCode(membership.getUser().getId(),roleCode(existing));
            userRoles.save(new UserRole(membership.getUser(),role(targetRole)));
        }
        try { staff.saveAndFlush(membership); userRoles.flush(); }
        catch (DataIntegrityViolationException ex) { throw duplicateStaffCode(); }
        return response(membership,targetRole);
    }

    private TransportOperator lockedOperator(Long id) { return operators.lockById(id).orElseThrow(OperatorStaffService::notFound); }
    private Role role(OperatorRole value) { return roles.findByCode(roleCode(value)).orElseThrow(); }
    private OperatorRole operatorRole(Long userId) {
        var codes=userRoles.findRoleCodesByUserId(userId);
        if (codes.size()!=1) throw membershipConflict();
        if (codes.get(0)==RoleCode.OPERATOR_ADMIN) return OperatorRole.OPERATOR_ADMIN;
        if (codes.get(0)==RoleCode.OPERATOR_STAFF) return OperatorRole.OPERATOR_STAFF;
        throw membershipConflict();
    }
    private static RoleCode roleCode(OperatorRole role) { return RoleCode.valueOf(role.name()); }
    private static OperatorStaffResponse response(OperatorStaff s, OperatorRole role) {
        User u=s.getUser();
        return new OperatorStaffResponse(s.getId(),s.getStaffCode(),s.getStatus(),role,
                new StaffUser(u.getId(),u.getFullName(),u.getEmail(),u.getPhone(),u.getStatus()),api(s.getCreatedAt()));
    }
    public static String email(String value) { return value.strip().toLowerCase(Locale.ROOT); }
    public static String staffCode(String value) { return value.strip().toUpperCase(Locale.ROOT); }
    private static BusinessException duplicateEmail() { return new BusinessException("EMAIL_ALREADY_EXISTS","Email is already registered."); }
    private static BusinessException duplicateStaffCode() { return new BusinessException("STAFF_CODE_ALREADY_EXISTS","Staff code already exists for this operator."); }
    private static BusinessException membershipConflict() { return new BusinessException("STAFF_MEMBERSHIP_CONFLICT","Staff membership conflicts with existing access."); }
    private static BusinessException lastAdmin() { return new BusinessException("LAST_OPERATOR_ADMIN_REQUIRED","The operator must retain an active administrator."); }
    private static BusinessException validation(String message) { return new BusinessException("VALIDATION_ERROR",message,HttpStatus.BAD_REQUEST,null); }
    private static ResourceNotFoundException notFound() { return new ResourceNotFoundException("STAFF_NOT_FOUND","Staff member was not found."); }
}
