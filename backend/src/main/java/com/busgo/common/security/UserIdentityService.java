package com.busgo.common.security;

import com.busgo.user.entity.*;
import com.busgo.user.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserIdentityService {
    private final UserRepository users;
    private final UserRoleRepository roles;
    public UserIdentityService(UserRepository users, UserRoleRepository roles) { this.users=users; this.roles=roles; }
    public static boolean active(User user) { return user.getStatus()==UserStatus.ACTIVE && user.getDeletedAt()==null; }
    @Transactional(readOnly=true)
    public CurrentUser load(Long id) {
        User user=users.findById(id).orElseThrow(AuthenticationFailure::credentials);
        if (!active(user)) throw AuthenticationFailure.credentials();
        return identity(user);
    }
    public CurrentUser identity(User user) { return new CurrentUser(user.getId(), roles.findRoleCodesByUserId(user.getId())); }
}
