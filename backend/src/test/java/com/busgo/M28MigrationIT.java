package com.busgo;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.core.env.Environment;
import org.springframework.transaction.support.TransactionTemplate;

/** Copies populated legacy fixtures into a new V24 schema; never cleans an existing schema. */
@SpringBootTest(properties="busgo.notifications.initial-delay=PT24H") @ActiveProfiles("dev")
class M28MigrationIT extends M20Support {
    @Autowired TransactionTemplate tx;
    @Autowired Environment env;
    @Autowired com.busgo.promotion.VoucherService vouchers;
    @Autowired com.busgo.booking.CancellationService cancellations;
    @Test void populatedV24UpgradePreservesEveryLegacyRow() {
        final String schema="busgo_m28_upgrade_"+UUID.randomUUID().toString().replace("-","");
        var fixture=tx.execute(s->{
            var f=fixture();var u=customer("m28-upgrade");var admin=actor(f,com.busgo.user.entity.RoleCode.OPERATOR_ADMIN);authenticate(admin);
            var start=java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).minusDays(1);
            var terms=new com.busgo.promotion.VoucherDtos.Terms("M28UPGRADE","Migration voucher",null,"FIXED_AMOUNT",new java.math.BigDecimal("10"),null,java.math.BigDecimal.ZERO,start,start.plusDays(10),100,10,null);
            long vid=((Number)vouchers.create(admin,terms).get("id")).longValue();vouchers.state(admin,vid,true);
            authenticate(u.user());var hold=hold(u,f,0,2,0);
            var b=bookingService.create(u.user(),new com.busgo.booking.BookingDtos.CreateBookingRequest(hold.holdToken(),"M28 migration","0901234567","upgrade@example.test","M28UPGRADE"));
            paymentService.confirm(u.user(),b.bookingId());cancellations.customerCancel(u.user(),b.bookingId(),new com.busgo.booking.CancellationDtos.Request("Migration history"));
            return f;
        });
        try {
            jdbc.execute("CREATE DATABASE "+schema);
            String original=env.getRequiredProperty("spring.datasource.url");
            String url=original.replaceFirst("/[^/?]+(?=\\?|$)","/"+schema);
            String user=env.getRequiredProperty("spring.datasource.username"),password=env.getRequiredProperty("spring.datasource.password");
            Flyway.configure().dataSource(url,user,password).target("24").load().migrate();
            var tables=jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name",String.class).stream()
                .filter(t->!t.equals("flyway_schema_history")&&!t.startsWith("support_")&&!t.equals("audit_events")).toList();
            var orderedTables=new ArrayList<>(tables);orderedTables.sort(Comparator.comparingInt(t->t.equals("refunds")?1:0));
            var columns=new LinkedHashMap<String,String>();
            for(String table:tables) columns.put(table,String.join(",",jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=? AND table_name=? AND generation_expression='' ORDER BY ordinal_position",String.class,schema,table).stream().map(c->"`"+c+"`").toList()));
            var expected=new LinkedHashMap<String,List<Map<String,Object>>>();
            for(String table:tables) {
                assertThat(table).matches("[a-z_]+");
                expected.put(table,jdbc.queryForList("SELECT * FROM "+table));
            }
            // Dedicated connection; FK checks are restored even on failure. All copied data is test-only.
            jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>)connection->{
                boolean auto=connection.getAutoCommit();connection.setAutoCommit(false);
                try(var st=connection.createStatement()) {
                    st.execute("SET FOREIGN_KEY_CHECKS=0");
                    try {
                        for(String table:orderedTables) {
                            // Restore refunds in their original insertion-time PAID context, then
                            // restore final statuses before establishing the V24 baseline snapshot.
                            if(table.equals("refunds")) st.executeUpdate("UPDATE "+schema+".payments SET status='PAID' WHERE status='REFUNDED'");
                            st.executeUpdate("INSERT "+(table.equals("roles")?"IGNORE ":"")+"INTO "+schema+"."+table+" ("+columns.get(table)+") SELECT "+columns.get(table)+" FROM "+table);
                        }
                        st.executeUpdate("UPDATE "+schema+".payments p JOIN payments original ON original.id=p.id SET p.status=original.status");
                        connection.commit();
                    }
                    catch(Exception e){connection.rollback();throw e;}
                    finally{st.execute("SET FOREIGN_KEY_CHECKS=1");connection.setAutoCommit(auto);}
                }return null;
            });
            for(String table:tables) assertThat(jdbc.queryForList("SELECT * FROM "+schema+"."+table)).as("V24 baseline "+table).containsExactlyInAnyOrderElementsOf(expected.get(table));
            var migration=Flyway.configure().dataSource(url,user,password).load();
            assertThat(migration.migrate().migrationsExecuted).isOne();migration.validate();
            assertThat(migration.info().applied()).hasSize(25);
            for(String table:tables) assertThat(jdbc.queryForList("SELECT * FROM "+schema+"."+table)).as(table).containsExactlyInAnyOrderElementsOf(expected.get(table));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+schema+".support_tickets",Long.class)).isZero();
        } finally {
            long oid=fixture.operator().getId();jdbc.update("DELETE FROM voucher_usages WHERE operator_id=?",oid);
            jdbc.update("UPDATE bookings SET voucher_id=NULL WHERE trip_id=?",fixture.trip().getId());
            jdbc.update("DELETE FROM voucher_management_history WHERE voucher_id IN (SELECT id FROM operator_vouchers WHERE operator_id=?)",oid);
            jdbc.update("DELETE FROM operator_vouchers WHERE operator_id=?",oid);
            NotificationTestCleanup.fixture(jdbc,fixture);org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
