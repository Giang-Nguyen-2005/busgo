package com.busgo;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class JwtTestSupport {
    public static final String SECRET;
    static { byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes); SECRET=Base64.getEncoder().encodeToString(bytes); }
    @DynamicPropertySource static void jwtProperties(DynamicPropertyRegistry registry) { registry.add("busgo.jwt.secret",()->SECRET); }
}
