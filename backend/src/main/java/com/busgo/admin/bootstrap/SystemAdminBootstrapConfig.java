package com.busgo.admin.bootstrap;

import org.slf4j.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration
@EnableConfigurationProperties(SystemAdminBootstrapProperties.class)
public class SystemAdminBootstrapConfig {
    private static final Logger log=LoggerFactory.getLogger(SystemAdminBootstrapConfig.class);
    @Bean
    ApplicationRunner systemAdminBootstrap(SystemAdminBootstrapProperties properties,
            SystemAdminBootstrapService service){
        return args->{
            if(!properties.enabled())return;
            boolean created=service.provision(properties);
            log.info(created?"Initial system administrator provisioned.":"Initial system administrator already provisioned; no changes made.");
        };
    }
}
