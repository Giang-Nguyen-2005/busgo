package com.busgo.admin.bootstrap;

import com.busgo.auth.PasswordPolicy;
import com.busgo.user.entity.*;
import com.busgo.user.repository.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemAdminBootstrapService {
    private final UserRepository users; private final RoleRepository roles;
    private final UserRoleRepository userRoles; private final PasswordEncoder passwords;
    public SystemAdminBootstrapService(UserRepository users,RoleRepository roles,
            UserRoleRepository userRoles,PasswordEncoder passwords){
        this.users=users;this.roles=roles;this.userRoles=userRoles;this.passwords=passwords;}
    @Transactional
    public boolean provision(SystemAdminBootstrapProperties p){
        String name=required(p.fullName(),"BUSGO_SYSTEM_ADMIN_FULL_NAME");
        String email=required(p.email(),"BUSGO_SYSTEM_ADMIN_EMAIL").toLowerCase(Locale.ROOT);
        String phone=required(p.phone(),"BUSGO_SYSTEM_ADMIN_PHONE");
        String password=required(p.password(),"BUSGO_SYSTEM_ADMIN_PASSWORD");
        if(name.length()>100||email.length()>150||phone.length()>20)
            throw new IllegalStateException("System administrator bootstrap identity exceeds database limits.");
        PasswordPolicy.validate(password);
        User existing=users.findByEmail(email).orElse(null);
        if(existing!=null){
            var codes=userRoles.findRoleCodesByUserId(existing.getId());
            boolean exact=existing.getDeletedAt()==null&&existing.getStatus()==UserStatus.ACTIVE
                    && name.equals(existing.getFullName())&&phone.equals(existing.getPhone())
                    && codes.equals(List.of(RoleCode.SYSTEM_ADMIN))
                    && passwords.matches(password,existing.getPasswordHash());
            if(!exact) throw new IllegalStateException("System administrator bootstrap email belongs to a different account or configuration.");
            return false;
        }
        User user=new User();user.setFullName(name);user.setEmail(email);user.setPhone(phone);
        user.setPasswordHash(passwords.encode(password));user.setStatus(UserStatus.ACTIVE);users.saveAndFlush(user);
        Role role=roles.findByCode(RoleCode.SYSTEM_ADMIN).orElseThrow(()->new IllegalStateException("SYSTEM_ADMIN role seed missing."));
        userRoles.saveAndFlush(new UserRole(user,role));return true;
    }
    private static String required(String value,String key){
        if(value==null||value.isBlank())throw new IllegalStateException(key+" is required when system administrator bootstrap is enabled.");
        return value.strip();
    }
}
