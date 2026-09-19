package com.busgo.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Collections;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserIdentityService identities;
    private final ObjectMapper mapper;
    public JwtAuthenticationFilter(JwtService jwt,UserIdentityService identities,ObjectMapper mapper) {
        this.jwt=jwt; this.identities=identities; this.mapper=mapper;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=request.getRequestURI().substring(request.getContextPath().length());
        return (request.getMethod().equals("GET") && path.equals("/api/v1/health"))
                || (request.getMethod().equals("POST") && java.util.Set.of("/api/v1/auth/register","/api/v1/auth/login","/api/v1/auth/refresh").contains(path));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        var headers=Collections.list(request.getHeaders("Authorization"));
        if (!headers.isEmpty()) {
            try {
                String header=headers.get(0);
                if (headers.size()!=1 || !header.regionMatches(true,0,"Bearer ",0,7)) throw AuthenticationFailure.credentials();
                var validated=jwt.validate(header.substring(7),"access");
                CurrentUser user=identities.load(Long.valueOf(validated.getSubject()));
                var context=SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user,null,user.authorities()));
                SecurityContextHolder.setContext(context);
            } catch (AuthenticationFailure ex) {
                SecurityContextHolder.clearContext();
                SecurityConfig.writeError(mapper,response,401,ex.getCode(),ex.getMessage());
                return;
            }
        }
        chain.doFilter(request,response);
    }
}
