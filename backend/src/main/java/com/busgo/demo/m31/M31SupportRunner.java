package com.busgo.demo.m31;

import com.busgo.audit.AuditService;
import com.busgo.media.OperatorMediaService;
import com.busgo.operations.OperationsService;
import com.busgo.promotion.VoucherService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.springframework.boot.*;
import org.springframework.context.annotation.*;

/** No component-scanned runner, web server, scheduler or legacy data initializer. */
public final class M31SupportRunner {
    private M31SupportRunner() {}
    public static void main(String[] args) throws Exception {
        var app=new SpringApplication(CommandConfiguration.class);app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(java.util.Map.of("spring.sql.init.mode","never"));
        try(var context=app.run(args)) {
            var env=context.getEnvironment();
            if(!env.getProperty("busgo.m31.support.enabled",Boolean.class,false)) throw new IllegalStateException("Explicit M31 support opt-in required");
            var assets=Path.of(env.getRequiredProperty("busgo.m31.support.assets"));
            var service=context.getBean(M31SupportService.class);
            boolean dry=env.getProperty("busgo.m31.support.dry-run",Boolean.class,true);
            System.out.println("M31_SUPPORT_REPORT="+new ObjectMapper().writeValueAsString(dry?service.dryRun(assets):service.apply(assets)));
        }
    }
    // Explicit source only: never annotate this class with @Configuration.
    @Profile("dev & !demo & !prod & !production")
    @Import({M31FleetRunner.CommandConfiguration.class,M31SupportService.class,OperatorMediaService.class,
        VoucherService.class,AuditService.class,OperationsService.class})
    public static class CommandConfiguration {}
}
