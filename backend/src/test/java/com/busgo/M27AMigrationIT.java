package com.busgo;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.time.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.core.env.Environment;
import org.springframework.transaction.support.TransactionTemplate;
import com.busgo.finance.*;

@SpringBootTest(properties="busgo.notifications.initial-delay=PT24H") @ActiveProfiles("dev")
class M27AMigrationIT extends M20Support {
    @Autowired TransactionTemplate tx;
    @Autowired Environment env;
    @Autowired com.busgo.promotion.VoucherService vouchers;
    @Autowired com.busgo.booking.CancellationService cancellations;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    String url(String schema) {return env.getRequiredProperty("spring.datasource.url").replaceFirst("/[^/?]+(?=\\?|$)","/"+schema);}
    Flyway flyway(String schema,String target) {return Flyway.configure().dataSource(url(schema),env.getRequiredProperty("spring.datasource.username"),env.getRequiredProperty("spring.datasource.password")).target(target).load();}
    @Test void cleanV1ThroughV26() {
        String schema="busgo_m27a_clean_"+UUID.randomUUID().toString().replace("-","");jdbc.execute("CREATE DATABASE "+schema);
        var migration=flyway(schema,"26");assertThat(migration.migrate().migrationsExecuted).isEqualTo(26);migration.validate();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+schema+".financial_events",Long.class)).isZero();
    }
    @Test void populatedV25UpgradePreservesSourcesAndIdempotentlyProjectsUnpricedHistory() {
        String schema="busgo_m27a_upgrade_"+UUID.randomUUID().toString().replace("-","");
        var f=tx.execute(s->expanded());var owner=tx.execute(s->customer("m27-upgrade"));
        var admin=tx.execute(s->actor(f,com.busgo.user.entity.RoleCode.OPERATOR_ADMIN));var platform=tx.execute(s->actor(f,com.busgo.user.entity.RoleCode.SYSTEM_ADMIN));
        authenticate(admin);var start=OffsetDateTime.now().minusDays(1);
        var terms=new com.busgo.promotion.VoucherDtos.Terms("M27UPGRADE","Migration",null,"FIXED_AMOUNT",new java.math.BigDecimal("10"),null,java.math.BigDecimal.ZERO,start,start.plusDays(10),100,10,null);
        long voucher=((Number)vouchers.create(admin,terms).get("id")).longValue();vouchers.state(admin,voucher,true);
        authenticate(owner.user());var hold=hold(owner,f,0,2,0);
        long booking=bookingService.create(owner.user(),new com.busgo.booking.BookingDtos.CreateBookingRequest(hold.holdToken(),"Upgrade","0901234567",null,"M27UPGRADE")).bookingId();
        paymentService.confirm(owner.user(),booking);cancellations.customerCancel(owner.user(),booking,new com.busgo.booking.CancellationDtos.Request(null));
        jdbc.execute("CREATE DATABASE "+schema);flyway(schema,"25").migrate();
        var tables=jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=? AND table_name<>'flyway_schema_history' ORDER BY table_name",String.class,schema);
        var ordered=new ArrayList<>(tables);ordered.sort(Comparator.comparingInt(t->t.equals("refunds")?1:0));
        var expected=new LinkedHashMap<String,List<Map<String,Object>>>();var columns=new LinkedHashMap<String,String>();
        for(String table:tables) {
            assertThat(table).matches("[a-z_]+");expected.put(table,jdbc.queryForList("SELECT * FROM "+table));
            columns.put(table,String.join(",",jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=? AND table_name=? AND generation_expression='' ORDER BY ordinal_position",String.class,schema,table).stream().map(c->"`"+c+"`").toList()));
        }
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>)connection->{
            try(var st=connection.createStatement()) {
                st.execute("SET FOREIGN_KEY_CHECKS=0");
                try {
                    for(String table:ordered) {
                        if(table.equals("refunds")) st.executeUpdate("UPDATE "+schema+".payments SET status='PAID' WHERE status='REFUNDED'");
                        st.executeUpdate("INSERT "+(table.equals("roles")?"IGNORE ":"")+"INTO "+schema+"."+table+" ("+columns.get(table)+") SELECT "+columns.get(table)+" FROM "+table);
                    }
                    st.executeUpdate("UPDATE "+schema+".payments p JOIN payments original ON original.id=p.id SET p.status=original.status");
                } finally {st.execute("SET FOREIGN_KEY_CHECKS=1");}
            }return null;
        });
        var migration=flyway(schema,"26");assertThat(migration.migrate().migrationsExecuted).isOne();migration.validate();
        for(String table:tables) assertThat(jdbc.queryForList("SELECT * FROM "+schema+"."+table)).as(table).containsExactlyInAnyOrderElementsOf(expected.get(table));
        var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource(url(schema),env.getRequiredProperty("spring.datasource.username"),env.getRequiredProperty("spring.datasource.password"));
        var db=new org.springframework.jdbc.core.JdbcTemplate(ds);var clock=Clock.systemUTC();
        var service=new FinanceService(db,new FinancialLedger(db,clock),null,new com.busgo.audit.AuditService(db,clock),json);
        var transaction=new TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
        var day=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));var input=new FinanceDtos.RunInput(f.operator().getId(),day.minusDays(1),day.plusDays(1),true);
        var first=transaction.execute(s->service.reconcile(platform,input));assertThat(first.projectedCount()).isEqualTo(2);assertThat(first.exceptionCount()).isEqualTo(2);
        assertThat(transaction.execute(s->service.reconcile(platform,input)).projectedCount()).isZero();
        assertThat(db.queryForList("SELECT commission FROM financial_events",java.math.BigDecimal.class)).containsOnlyNulls();
        for(String table:List.of("payments","refunds","bookings","booking_items","voucher_usages"))
            assertThat(db.queryForList("SELECT * FROM "+table)).as(table).containsExactlyInAnyOrderElementsOf(expected.get(table));
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
