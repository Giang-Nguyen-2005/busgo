package com.busgo.demo.m31;

import com.busgo.location.repository.LocationRepository;
import com.busgo.route.RouteDefinitionService;
import com.busgo.route.repository.RouteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Explicit command, never discovered at ordinary application startup.
 * Minimal context: no Flyway, web controllers, bootstrap, seeders or scheduled jobs.
 */
public final class M31CatalogueRunner {
    private M31CatalogueRunner() {}
    public static void main(String[] args) throws Exception {
        var app=new SpringApplication(CommandConfiguration.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(java.util.Map.of("spring.sql.init.mode","never"));
        try(var context=app.run(args)) {
            var env=context.getEnvironment();
            if(!env.getProperty("busgo.m31.catalogue.enabled",Boolean.class,false))
                throw new IllegalStateException("Explicit --busgo.m31.catalogue.enabled=true is required");
            var batch=env.getProperty("busgo.m31.catalogue.batch",M31Catalogue.Batch.class,M31Catalogue.Batch.PILOT);
            boolean dry=env.getProperty("busgo.m31.catalogue.dry-run",Boolean.class,true);
            var service=context.getBean(M31CatalogueService.class);
            var report=dry?service.dryRun(batch):service.apply(batch);
            System.out.println("M31_CATALOGUE_REPORT="+new ObjectMapper().writeValueAsString(report));
        }
    }
    // Explicit SpringApplication source, deliberately not a scanned @Configuration component.
    @Profile("dev & !demo & !prod & !production")
    @EnableAutoConfiguration(exclude=FlywayAutoConfiguration.class)
    @EntityScan(basePackages="com.busgo")
    @EnableJpaRepositories(basePackageClasses={LocationRepository.class,RouteRepository.class})
    @Import({M31CatalogueService.class,RouteDefinitionService.class})
    public static class CommandConfiguration {}
}
