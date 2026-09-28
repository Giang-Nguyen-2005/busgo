package com.busgo.admin;

import static com.busgo.admin.AdminOperatorDtos.*;
import static com.busgo.operator.OperatorStaffDtos.*;
import com.busgo.auth.PasswordPolicy;
import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.exception.*;
import com.busgo.common.response.PagedResponse;
import com.busgo.operator.*;
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

@Service
public class AdminOperatorService {
    private final TransportOperatorRepository operators;
    private final OperatorStaffRepository staff;
    private final OperatorStaffQueryRepository staffQueries;
    private final AdminOperatorQueryRepository queries;
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final PasswordEncoder passwords;
    public AdminOperatorService(TransportOperatorRepository operators,OperatorStaffRepository staff,
            OperatorStaffQueryRepository staffQueries,AdminOperatorQueryRepository queries,
            UserRepository users,RoleRepository roles,UserRoleRepository userRoles,PasswordEncoder passwords) {
        this.operators=operators; this.staff=staff; this.staffQueries=staffQueries; this.queries=queries;
        this.users=users; this.roles=roles; this.userRoles=userRoles; this.passwords=passwords;
    }
    @Transactional(readOnly=true)
    public PagedResponse<AdminOperatorListItem> list(String q,OperatorStatus status,int page,int size) {
        var r=queries.search(q,status,page,size); int pages=(int)Math.ceil(r.total()/(double)size);
        return new PagedResponse<>(r.rows(),new PagedResponse.Pagination(page,size,r.total(),pages));
    }
    @Transactional
    public AdminOperatorDetail create(CreateOperatorRequest request) {
        PasswordPolicy.validate(request.initialAdmin().password());
        String code=normalizeCode(request.code()); String email=OperatorStaffService.email(request.initialAdmin().email());
        String staffCode=OperatorStaffService.staffCode(request.initialAdmin().staffCode());
        if (operators.existsByCodeIgnoreCase(code)) throw duplicateCode();
        if (users.existsByEmail(email)) throw duplicateEmail();
        TransportOperator operator=new TransportOperator(); operator.setCode(code); operator.setName(request.name().strip());
        operator.setPhone(trim(request.phone())); operator.setEmail(emailOrNull(request.email())); operator.setAddress(trim(request.address()));
        operator.setStatus(request.status());
        try { operators.saveAndFlush(operator); }
        catch (DataIntegrityViolationException ex) { throw duplicateCode(); }
        User user=new User(); user.setFullName(request.initialAdmin().fullName().strip()); user.setEmail(email);
        user.setPhone(request.initialAdmin().phone().strip()); user.setPasswordHash(passwords.encode(request.initialAdmin().password()));
        user.setStatus(UserStatus.ACTIVE);
        try { users.saveAndFlush(user); }
        catch (DataIntegrityViolationException ex) { throw duplicateEmail(); }
        Role admin=roles.findByCode(RoleCode.OPERATOR_ADMIN).orElseThrow();
        userRoles.saveAndFlush(new UserRole(user,admin));
        OperatorStaff membership=new OperatorStaff(); membership.setOperator(operator); membership.setUser(user);
        membership.setStaffCode(staffCode); membership.setStatus(ActiveStatus.ACTIVE); staff.saveAndFlush(membership);
        return detail(operator.getId());
    }
    @Transactional(readOnly=true)
    public AdminOperatorDetail detail(Long id) { return queries.detail(id).orElseThrow(AdminOperatorService::notFound); }
    @Transactional
    public AdminOperatorDetail update(Long id,UpdateOperatorRequest request) {
        if (request.name()==null&&request.phone()==null&&request.email()==null&&request.address()==null)
            throw validation("At least one operator field is required.");
        TransportOperator o=operators.lockById(id).orElseThrow(AdminOperatorService::notFound);
        if(request.name()!=null)o.setName(request.name().strip()); if(request.phone()!=null)o.setPhone(trim(request.phone()));
        if(request.email()!=null)o.setEmail(emailOrNull(request.email())); if(request.address()!=null)o.setAddress(trim(request.address()));
        operators.saveAndFlush(o); return detail(id);
    }
    @Transactional
    public AdminOperatorDetail status(Long id,UpdateOperatorStatusRequest request) {
        TransportOperator o=operators.lockById(id).orElseThrow(AdminOperatorService::notFound);
        if(o.getStatus()==request.status()) return detail(id);
        if(request.status()==OperatorStatus.ACTIVE&&staff.countActiveLoginCapableAdmins(id)<1)
            throw new BusinessException("OPERATOR_ACTIVATION_NOT_ALLOWED","An active operator administrator is required.");
        o.setStatus(request.status()); operators.saveAndFlush(o); return detail(id);
    }
    @Transactional(readOnly=true)
    public PagedResponse<OperatorStaffResponse> staff(Long id,String q,ActiveStatus status,OperatorRole role,int page,int size) {
        if(!operators.existsById(id))throw notFound(); var r=staffQueries.search(id,q,status,role,page,size);
        int pages=(int)Math.ceil(r.total()/(double)size);
        return new PagedResponse<>(r.rows(),new PagedResponse.Pagination(page,size,r.total(),pages));
    }
    private static String normalizeCode(String v){return v.strip().toUpperCase(Locale.ROOT);}
    private static String trim(String v){return v==null?null:v.strip();}
    private static String emailOrNull(String v){return v==null?null:v.strip().toLowerCase(Locale.ROOT);}
    private static ResourceNotFoundException notFound(){return new ResourceNotFoundException("OPERATOR_NOT_FOUND","Operator was not found.");}
    private static BusinessException duplicateCode(){return new BusinessException("OPERATOR_CODE_ALREADY_EXISTS","Operator code already exists.");}
    private static BusinessException duplicateEmail(){return new BusinessException("EMAIL_ALREADY_EXISTS","Email is already registered.");}
    private static BusinessException validation(String m){return new BusinessException("VALIDATION_ERROR",m,HttpStatus.BAD_REQUEST,null);}
}
