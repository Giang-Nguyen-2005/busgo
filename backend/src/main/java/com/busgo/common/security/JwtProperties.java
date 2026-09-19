package com.busgo.common.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "busgo.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTtl, Duration refreshTtl) {
    @Override public String toString() { return "JwtProperties[secret=REDACTED]"; }
}
