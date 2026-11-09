package com.busgo.demo.m31;

import com.busgo.fleet.*;
import com.busgo.operator.OperatorContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.boot.*;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Standalone opt-in command; never an ordinary startup runner. No scheduler, web, bootstrap or Flyway. */
public final class M31FleetRunner {
    private M31FleetRunner() {}
    public static void main(String[] args) throws Exception {
        var app=new SpringApplication(CommandConfiguration.class); app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(java.util.Map.of("spring.sql.init.mode","never"));
        try(var context=app.run(args)) {
            var env=context.getEnvironment();
            if(!env.getProperty("busgo.m31.fleet.enabled",Boolean.class,false)) throw new IllegalStateException("Explicit M31 fleet opt-in required");
            boolean dry=env.getProperty("busgo.m31.fleet.dry-run",Boolean.class,true);
            boolean profiles=env.getProperty("busgo.m31.fleet.fill-blank-profiles",Boolean.class,false);
            var service=context.getBean(M31FleetService.class);
            System.out.println("M31_FLEET_REPORT="+new ObjectMapper().writeValueAsString(dry?service.dryRun(profiles):service.apply(profiles)));
        }
    }
    // Explicit source only: deliberately not a component-scanned @Configuration.
    @Profile("dev & !demo & !prod & !production")
    @EnableJpaRepositories(basePackages={"com.busgo.fleet.repository","com.busgo.operator.repository","com.busgo.user.repository"})
    @Import({M31CatalogueRunner.CommandConfiguration.class,M31FleetService.class,BusService.class,BusTypeService.class,
            OperatorContextService.class,MaintenanceService.class,FleetMaintenanceGuard.class})
    public static class CommandConfiguration {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    }
}
