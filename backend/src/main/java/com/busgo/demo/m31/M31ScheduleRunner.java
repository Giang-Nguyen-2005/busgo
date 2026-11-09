package com.busgo.demo.m31;

import com.busgo.operator.OperatorCommerceGuard;
import com.busgo.route.*;
import com.busgo.trip.TripAggregateCreator;
import com.fasterxml.jackson.databind.*;
import java.time.LocalDate;
import org.springframework.boot.*;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

public final class M31ScheduleRunner {
    private M31ScheduleRunner() {}
    public static void main(String[] args) throws Exception {
        var app=new SpringApplication(CommandConfiguration.class); app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(java.util.Map.of("spring.sql.init.mode","never"));
        try(var context=app.run(args)) {
            var env=context.getEnvironment();
            if(!env.getProperty("busgo.m31.schedule.enabled",Boolean.class,false)) throw new IllegalStateException("Explicit M31 schedule opt-in required");
            String anchor=env.getRequiredProperty("busgo.m31.schedule.anchor");
            int horizon=env.getProperty("busgo.m31.schedule.horizon",Integer.class,14);
            boolean placement=env.getProperty("busgo.m31.schedule.acknowledge-initial-placement",Boolean.class,false);
            boolean dry=env.getProperty("busgo.m31.schedule.dry-run",Boolean.class,true);
            var service=context.getBean(M31ScheduleService.class);
            var report=dry?service.dryRun(LocalDate.parse(anchor),horizon,placement):service.apply(LocalDate.parse(anchor),horizon,placement);
            System.out.println("M31_SCHEDULE_REPORT="+new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).writeValueAsString(report));
            if(report.batches().stream().anyMatch(M31Schedule.Batch::rolledBack)) throw new IllegalStateException("Rotation batches rolled back; review the emitted partial-completion report");
        }
    }
    // Explicit standalone source only, not discovered at application startup.
    @Profile("dev & !demo & !prod & !production")
    @EnableJpaRepositories(basePackages="com.busgo.trip.repository")
    @Import({M31FleetRunner.CommandConfiguration.class,M31ScheduleBatchService.class,M31ScheduleService.class,
            OperatorRouteService.class,OperatorRouteFareService.class,OperatorCommerceGuard.class,TripAggregateCreator.class})
    public static class CommandConfiguration {}
}
