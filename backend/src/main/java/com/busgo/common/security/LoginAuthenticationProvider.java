package com.busgo.common.security;

import com.busgo.auth.PasswordPolicy;
import com.busgo.user.repository.UserRepository;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LoginAuthenticationProvider implements AuthenticationProvider {
    private final UserRepository users;
    private final UserIdentityService identities;
    private final PasswordEncoder passwords;
    private final String dummyHash;
    public LoginAuthenticationProvider(UserRepository users, UserIdentityService identities, PasswordEncoder passwords) {
        this.users=users; this.identities=identities; this.passwords=passwords;
        dummyHash=passwords.encode(java.util.UUID.randomUUID().toString());
    }
    @Override @Transactional
    public Authentication authenticate(Authentication authentication) {
        String password=(String) authentication.getCredentials();
        if (!PasswordPolicy.bcryptCompatible(password)) throw AuthenticationFailure.credentials();
        var user=users.findLockedByEmail(authentication.getName()).orElse(null);
        boolean matches=passwords.matches(password,user==null ? dummyHash : user.getPasswordHash());
        if (!matches || user==null || !UserIdentityService.active(user)) throw AuthenticationFailure.credentials();
        CurrentUser principal=identities.identity(user);
        return UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.authorities());
    }
    @Override public boolean supports(Class<?> type) { return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type); }
}
