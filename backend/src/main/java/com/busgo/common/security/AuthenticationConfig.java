package com.busgo.common.security;

import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class AuthenticationConfig {
    @Bean Clock authenticationClock() { return Clock.systemUTC(); }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean AuthenticationManager authenticationManager(LoginAuthenticationProvider provider) { return new ProviderManager(provider); }
}
