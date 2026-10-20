package com.busgo;

import static org.assertj.core.api.Assertions.*;
import com.busgo.booking.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** Append-only upgrade of an isolated V23 schema with paid/modified/partially-cancelled history. */
@SpringBootTest @ActiveProfiles("dev")
class M24BMigrationIT extends M20Support {
    @Autowired DataSource source;
    @Autowired TransactionTemplate tx;
    @Autowired PartialCancellationService partial;
    @Autowired CancellationService cancellation;
    @Test void upgradeHistoricalV23PreservesNetAndPayments() throws Exception {
        var f=tx.execute(s->expanded());var owner=tx.execute(s->customer("m24b-upgrade"));
        try {
            authenticate(owner.user());var b=web(f,owner,0,1);paymentService.confirm(owner.user(),b.bookingId());
            var mod=modifications.create(owner.user(),b.bookingId(),false,seatRequest(b.bookingId(),f,3));modifications.confirm(owner.user(),b.bookingId(),false,mod.id());
            partial.execute(owner.user(),b.bookingId(),false,new PartialCancellationDtos.Request(List.of(itemIds(b.bookingId()).get(0))));
            var unpaid=web(f,owner,2);cancellation.customerCancel(owner.user(),unpaid.bookingId(),new CancellationDtos.Request(null));
            // Every copied table is from this isolated integration schema, never the development database.
            try(var connection=source.getConnection()) {
                String from=connection.getCatalog();assertThat(from).matches("busgo_m(?:24b|28)_test");
                String schema="busgo_m24b_upgrade_"+UUID.randomUUID().toString().replace("-","");
                try(var statement=connection.createStatement()) { statement.execute("CREATE DATABASE `"+schema+"`"); }
                String url=connection.getMetaData().getURL().replace("/"+from,"/"+schema);
                String user=System.getenv().getOrDefault("DB_USERNAME","root"),password=System.getenv().getOrDefault("DB_PASSWORD","");
                Flyway.configure().dataSource(url,user,password).target("23").load().migrate();
                var tables=jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=? AND table_name<>'flyway_schema_history'",String.class,schema);
                tables.sort(Comparator.comparingInt(t->"refunds".equals(t)?1:0));
                try(var statement=connection.createStatement()) {
                    statement.execute("SET FOREIGN_KEY_CHECKS=0");
                    try {
                        for(String table:tables) {
                            var columns=jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=? AND table_name=? AND generation_expression='' ORDER BY ordinal_position",String.class,schema,table);
                            String names=columns.stream().map(x->"`"+x+"`").collect(java.util.stream.Collectors.joining(","));
                            // V2 has already seeded role rows in the new V23 schema.
                            statement.execute("INSERT "+("roles".equals(table)?"IGNORE ":"")+"INTO `"+schema+"`.`"+table+"` ("+names+") SELECT "+names+" FROM `"+from+"`.`"+table+"`");
                        }
                    } finally {statement.execute("SET FOREIGN_KEY_CHECKS=1");}
                }
                var flyway=Flyway.configure().dataSource(url,user,password).target("24").load();assertThat(flyway.migrate().migrationsExecuted).isOne();flyway.validate();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM `"+schema+"`.bookings WHERE discount_amount<>0 OR gross_amount<>total_amount",Long.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM `"+schema+"`.booking_items WHERE discount_amount<>0 OR gross_amount<>unit_price",Long.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM `"+schema+"`.payments p JOIN payments old ON old.id=p.id WHERE p.amount<>old.amount OR NOT(p.paid_at<=>old.paid_at)",Long.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT original_gross_amount FROM `"+schema+"`.bookings WHERE id=?",java.math.BigDecimal.class,b.bookingId())).isEqualByComparingTo("400");
            }
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            tx.executeWithoutResult(s->{NotificationTestCleanup.fixture(jdbc,f);jdbc.update("DELETE FROM user_roles WHERE user_id=?",owner.user().id());jdbc.update("DELETE FROM users WHERE id=?",owner.user().id());});
        }
    }
}
