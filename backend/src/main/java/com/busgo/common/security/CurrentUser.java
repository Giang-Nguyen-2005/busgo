package com.busgo.common.security;

import com.busgo.user.entity.RoleCode;
import java.security.Principal;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record CurrentUser(Long id, List<RoleCode> roles) implements Principal {
    public CurrentUser { roles = List.copyOf(roles); }
    @Override public String getName() { return id.toString(); }
    public List<SimpleGrantedAuthority> authorities() {
        return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
    }
}
