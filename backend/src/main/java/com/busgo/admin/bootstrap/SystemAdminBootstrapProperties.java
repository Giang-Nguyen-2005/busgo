package com.busgo.admin.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="busgo.system-admin-bootstrap")
public record SystemAdminBootstrapProperties(boolean enabled, String fullName,
        String email, String phone, String password) {
    @Override public String toString(){return "SystemAdminBootstrapProperties[enabled="+enabled+", credentials=REDACTED]";}
}
