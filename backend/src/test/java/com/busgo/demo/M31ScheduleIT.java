package com.busgo.demo;

import com.busgo.JwtTestSupport;
import com.busgo.demo.m31.*;
import com.busgo.common.time.*;
import com.busgo.trip.TripAggregateCreator;
import jakarta.persistence.EntityManager;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="busgo.m31.catalogue.verified-schema=busgo_m312_test")
@ActiveProfiles("dev")
@Transactional
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="busgo.m31.schedule.integration",matches="true")
class M31ScheduleIT extends JwtTestSupport {
    private static final LocalDate ANCHOR=LocalDate.of(2030,2,2);
    @Autowired M31ScheduleBatchService batches;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired TripAggregateCreator creator;
    @Test void dryCreateRerunAndCancelledReusePreserveOldRowsAndGenerateCompleteAggregates() {
        var before=M31FleetIT.snapshot(jdbc);
        var dry=batches.dryRun(ANCHOR,2,"51B-770.02",true);
        assertThat(dry.departures()).hasSize(4).allMatch(d->d.action().equals("CREATE")&&d.seats()==40&&d.inventory()==40);
        assertThat(M31FleetIT.snapshot(jdbc)).isEqualTo(before);
        var applied=batches.apply(ANCHOR,2,"51B-770.02",true);
        assertThat(applied.departures()).allMatch(d->d.action().equals("CREATED"));
        var after=M31FleetIT.snapshot(jdbc);
        for(var table:before.keySet()) assertThat(after.get(table)).as(table).containsAll(before.get(table));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operator_route_fares WHERE price=160000",Long.class)).isEqualTo(2);
        for(var d:applied.departures()) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_stops WHERE trip_id=?",Long.class,d.tripId())).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_seats WHERE trip_id=?",Long.class,d.tripId())).isEqualTo(40);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory i JOIN trip_seats s ON s.id=i.trip_seat_id WHERE s.trip_id=? AND i.status='AVAILABLE'",Long.class,d.tripId())).isEqualTo(40);
        }
        jdbc.update("UPDATE trips SET status='CANCELLED' WHERE id=?",applied.departures().get(0).tripId()); em.clear();
        var cancelled=M31FleetIT.snapshot(jdbc);
        assertThat(batches.apply(ANCHOR,2,"51B-770.02",true).departures()).allMatch(d->d.action().equals("REUSE"));
        assertThat(M31FleetIT.snapshot(jdbc)).isEqualTo(cancelled);
    }
    @Test void legacyFareChangesAreUsedWithoutOverwritingPriceOrHistory() {
        long association=association("DEMO-ANPHU","TP. Hồ Chí Minh → Đà Lạt");
        jdbc.update("UPDATE operator_route_fares SET price=123456 WHERE operator_route_id=?",association); em.clear();
        var fares=M31FleetIT.snapshot(jdbc).get("operator_route_fares");
        var report=batches.apply(ANCHOR,1,"51B-770.01",true);
        assertThat(report.catalogue()).filteredOn(e->e.kind().equals("FARE")).anyMatch(e->e.price().intValueExact()==123456);
        assertThat(report.departures()).allMatch(d->d.action().equals("CREATED"));
        assertThat(M31FleetIT.snapshot(jdbc).get("operator_route_fares")).isEqualTo(fares);
    }
    @Test void maintenanceManualOverlapPastAndMissingPlacementNeverCreateBrokenRotations() {
        long bus=jdbc.queryForObject("SELECT id FROM buses WHERE license_plate='DEMO-M31-AP-01'",Long.class);
        long actor=jdbc.queryForObject("SELECT id FROM users WHERE email='operator.admin@anphu-demo.example'",Long.class);
        var start=BusGoTime.businessTime(ANCHOR,LocalTime.of(6,0));
        jdbc.update("INSERT INTO bus_maintenance_records(bus_id,maintenance_type,status,title,scheduled_start,scheduled_end,created_by,created_at,updated_at) VALUES(?,'OTHER','SCHEDULED','M31 rollback fixture',?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
            bus,JpaJdbcTime.parameter(start),JpaJdbcTime.parameter(start.plusHours(3)),actor);
        var before=M31FleetIT.snapshot(jdbc);
        assertThat(batches.apply(ANCHOR,1,"DEMO-M31-AP-01",true).departures()).allMatch(d->d.action().equals("SKIP")&&d.reason().contains("MAINTENANCE"));
        assertThat(M31FleetIT.snapshot(jdbc)).isEqualTo(before);
        jdbc.update("UPDATE bus_maintenance_records SET status='CANCELLED' WHERE bus_id=?",bus);
        long op=jdbc.queryForObject("SELECT id FROM transport_operators WHERE code='DEMO-ANPHU'",Long.class);
        creator.create(op,association("DEMO-ANPHU","TP. Hồ Chí Minh → Nha Trang"),bus,start.plusHours(1));
        var manual=M31FleetIT.snapshot(jdbc);
        assertThat(batches.apply(ANCHOR,1,"DEMO-M31-AP-01",true).departures()).allMatch(d->d.action().equals("SKIP")&&d.reason().contains("SCHEDULE_CONFLICT"));
        assertThat(M31FleetIT.snapshot(jdbc)).isEqualTo(manual);
        assertThat(batches.dryRun(LocalDate.of(2001,1,1),1,"DEMO-M31-AP-02",true).departures()).allMatch(d->d.reason().contains("PAST"));
        assertThat(batches.dryRun(ANCHOR,1,"DEMO-M31-AP-02",false).departures()).allMatch(d->d.reason().contains("PLACEMENT"));
    }
    @Test void inactiveAssociationAndWrongFareStopsAreReportedRatherThanRepaired() {
        long op=jdbc.queryForObject("SELECT id FROM transport_operators WHERE code='DEMO-ANPHU'",Long.class);
        long route=jdbc.queryForObject("SELECT id FROM routes WHERE name='Demo · TP. Hồ Chí Minh → Cần Thơ'",Long.class);
        jdbc.update("INSERT INTO operator_routes(operator_id,route_id,status,created_at,updated_at) VALUES(?,?,'INACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",op,route); em.clear();
        var before=M31FleetIT.snapshot(jdbc);
        assertThat(batches.apply(ANCHOR,1,"51B-770.02",true).departures()).allMatch(d->d.reason().contains("INACTIVE_ASSOCIATION"));
        assertThat(M31FleetIT.snapshot(jdbc)).isEqualTo(before);
        long a=association("DEMO-ANPHU","Demo · TP. Hồ Chí Minh → Cần Thơ");
        jdbc.update("UPDATE operator_routes SET status='ACTIVE' WHERE id=?",a);
        var stopIds=jdbc.queryForList("SELECT id FROM route_stops WHERE route_id=? ORDER BY stop_order",Long.class,route);
        jdbc.update("INSERT INTO operator_route_fares(operator_route_id,from_route_stop_id,to_route_stop_id,price,status,created_at,updated_at) VALUES(?,?,?,1,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",a,stopIds.get(1),stopIds.get(0)); em.clear();
        var wrong=M31FleetIT.snapshot(jdbc);
        assertThat(batches.apply(ANCHOR,1,"51B-770.02",true).departures()).allMatch(d->d.reason().contains("FARE_IDENTITY"));
        assertThat(M31FleetIT.snapshot(jdbc)).isEqualTo(wrong);
    }
    private long association(String owner,String route) {
        return jdbc.queryForObject("SELECT a.id FROM operator_routes a JOIN routes r ON r.id=a.route_id JOIN transport_operators o ON o.id=a.operator_id WHERE o.code=? AND r.name=?",Long.class,owner,route);
    }
}
