package com.busgo;

import static org.assertj.core.api.Assertions.*;
import com.busgo.promotion.VoucherDtos.Terms;
import com.busgo.promotion.VoucherService;
import com.busgo.user.entity.RoleCode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest @ActiveProfiles("dev")
class M30VoucherVisibilityMigrationIT extends M20Support {
    @Autowired TransactionTemplate tx;
    @Autowired Environment env;
    @Autowired VoucherService vouchers;
    @Test void populatedV26DefaultsPrivateAndPreservesExistingTerms() {
        String sourceSchema=jdbc.queryForObject("SELECT DATABASE()",String.class);
        if ("busgo_m306_verify".equals(sourceSchema))
            assertThat(env.getRequiredProperty("spring.datasource.url")).startsWith("jdbc:mysql://127.0.0.1:3346/busgo_m306_verify?");
        else assertThat(sourceSchema).isEqualTo("busgo_m30_4a_test");
        tx.executeWithoutResult(s->{
            var f=fixture();var admin=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(admin);
            vouchers.create(admin,new Terms("LEGACY","Legacy private voucher",null,"FIXED_AMOUNT",new BigDecimal("10"),null,BigDecimal.ZERO,OffsetDateTime.now().minusDays(1),OffsetDateTime.now().plusDays(1),null,null,null));
        });
        String schema="busgo_m304a_upgrade_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("CREATE DATABASE "+schema);
        String url=env.getRequiredProperty("spring.datasource.url").replaceFirst("/[^/?]+(?=\\?|$)","/"+schema);
        String user=env.getRequiredProperty("spring.datasource.username"),password=env.getRequiredProperty("spring.datasource.password");
        Flyway.configure().dataSource(url,user,password).target("26").load().migrate();
        String voucherColumns="";
        for(String table:List.of("users","transport_operators","locations","routes","operator_routes","operator_vouchers")) {
            var columns=jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=? AND table_name=? ORDER BY ordinal_position",String.class,schema,table);
            String names=String.join(",",columns.stream().map(c->"`"+c+"`").toList());
            jdbc.update("INSERT INTO "+schema+"."+table+" ("+names+") SELECT "+names+" FROM "+table);
            if(table.equals("operator_vouchers"))voucherColumns=names;
        }
        var before=jdbc.queryForList("SELECT "+voucherColumns+" FROM "+schema+".operator_vouchers");
        var migration=Flyway.configure().dataSource(url,user,password).target("27").load();
        assertThat(migration.migrate().migrationsExecuted).isOne();migration.validate();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+schema+".operator_vouchers WHERE customer_visible<>FALSE",Long.class)).isZero();
        assertThat(jdbc.queryForList("SELECT "+voucherColumns+" FROM "+schema+".operator_vouchers")).containsExactlyInAnyOrderElementsOf(before);
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
