package com.busgo;

import static org.assertj.core.api.Assertions.*;
import com.busgo.common.exception.BusinessException;
import com.busgo.common.security.CurrentUser;
import com.busgo.fleet.*;
import com.busgo.fleet.MaintenanceDtos.*;
import com.busgo.trip.TripAggregateCreator;
import com.busgo.trip.entity.TripStatus;
import com.busgo.trip.operations.OperatorTripOperationsService;
import com.busgo.operations.*;
import com.busgo.operations.OperationsDtos.*;
import com.busgo.user.entity.RoleCode;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest @ActiveProfiles("dev")
class M18CFleetConcurrencyIT extends M16ATestSupport {
    @Autowired TransactionTemplate transactions;
    @Autowired MaintenanceService maintenance;
    @Autowired TripAggregateCreator creator;
    @Autowired OperationsService operations;
    @Autowired OperatorTripOperationsService lifecycle;
    private Fixture f;
    private CurrentUser admin;
    @BeforeEach void setup() { f=transactions.execute(s->fixture());admin=transactions.execute(s->actor(f,RoleCode.OPERATOR_ADMIN));authenticate(admin); }
    private Schedule window(LocalDateTime start,LocalDateTime end) { return new Schedule(Type.REPAIR,"Concurrency test",null,start.atOffset(ZoneOffset.UTC),end.atOffset(ZoneOffset.UTC)); }
    private List<String> race(Runnable a,Runnable b) throws Exception {
        var executor=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            var x=executor.submit(()->attempt(gate,a));var y=executor.submit(()->attempt(gate,b));gate.countDown();
            return List.of(x.get(20,TimeUnit.SECONDS),y.get(20,TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }
    private String attempt(CountDownLatch gate,Runnable work) throws Exception {
        authenticate(admin);try {gate.await();work.run();return "OK";}catch(BusinessException e){return e.getCode();}finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }
    @Test void maintenanceAndTripCreationCannotBothReserveSameWindow() throws Exception {
        var start=f.trip().getEstimatedArrivalTime().plusDays(1);var end=start.plusHours(3);
        var outcomes=race(()->maintenance.schedule(admin,f.bus().getId(),window(start,end)),()->creator.create(f.operator().getId(),f.operatorRoute().getId(),f.bus().getId(),start));
        assertThat(outcomes.stream().filter("OK"::equals).count()).isOne();
        assertThat(outcomes).allMatch(s->Set.of("OK","BUS_MAINTENANCE_CONFLICT","MAINTENANCE_TRIP_CONFLICT","FLEET_PLAN_CHANGED").contains(s));
    }
    @Test void concurrentStartsHaveExactlyOneWinner() throws Exception {
        var m=maintenance.schedule(admin,f.bus().getId(),window(f.trip().getDepartureTime().minusDays(2),f.trip().getDepartureTime().minusDays(1)));
        assertThat(race(()->maintenance.start(admin,m.id()),()->maintenance.start(admin,m.id()))).containsExactlyInAnyOrder("OK","INVALID_MAINTENANCE_TRANSITION");
        assertThat(maintenance.history(admin,f.bus().getId())).hasSize(1);
    }
    @Test void boardingAndMaintenanceStartSerializeWithoutDeadlock() throws Exception {
        var driver=operations.saveEmployee(admin,null,new EmployeeInput("M18C","Driver","0901234567",EmployeeStatus.ACTIVE,Set.of(Capability.DRIVER),"LICENCE","D",LocalDate.of(2099,1,1),null));
        operations.replaceCrew(admin,f.trip().getId(),new CrewReplacement(List.of(new CrewInput(driver.id(),Capability.DRIVER))));
        var m=maintenance.schedule(admin,f.bus().getId(),window(f.trip().getDepartureTime().minusDays(2),f.trip().getDepartureTime().minusDays(1)));
        var outcomes=race(()->maintenance.start(admin,m.id()),()->lifecycle.updateStatus(admin,f.trip().getId(),TripStatus.BOARDING));
        assertThat(outcomes.stream().filter("OK"::equals).count()).isOne();
        assertThat(outcomes).allMatch(s->Set.of("OK","CREW_NOT_READY","MAINTENANCE_TRIP_CONFLICT").contains(s));
    }
    @AfterEach void cleanup() {
        if(f==null) return;
        long operator=f.operator().getId();
        jdbc.update("DELETE h FROM bus_status_history h JOIN buses b ON b.id=h.bus_id WHERE b.operator_id=?",operator);
        jdbc.update("DELETE m FROM bus_maintenance_records m JOIN buses b ON b.id=m.bus_id WHERE b.operator_id=?",operator);
        M16BFixtures.clear(jdbc,operator,f.trip().getId());
        var ownedTrips=jdbc.queryForList("SELECT t.id FROM trips t JOIN operator_routes o ON o.id=t.operator_route_id WHERE o.operator_id=?",Long.class,operator);
        for(long id:ownedTrips) {
            jdbc.update("DELETE FROM trip_seat_segment_inventory WHERE trip_seat_id IN (SELECT id FROM trip_seats WHERE trip_id=?)",id);
            jdbc.update("DELETE FROM trip_seats WHERE trip_id=?",id);jdbc.update("DELETE FROM trip_segments WHERE trip_id=?",id);jdbc.update("DELETE FROM trip_stops WHERE trip_id=?",id);jdbc.update("DELETE FROM trips WHERE id=?",id);
        }
        jdbc.update("DELETE FROM operator_route_fares WHERE operator_route_id=?",f.operatorRoute().getId());
        jdbc.update("DELETE FROM buses WHERE operator_id=?",operator);
        jdbc.update("DELETE FROM seat_templates WHERE bus_type_id=?",f.busType().getId());jdbc.update("DELETE FROM bus_types WHERE id=?",f.busType().getId());
        jdbc.update("DELETE FROM operator_routes WHERE operator_id=?",operator);jdbc.update("DELETE FROM route_stops WHERE route_id=?",f.route().getId());jdbc.update("DELETE FROM routes WHERE id=?",f.route().getId());
        jdbc.update("DELETE FROM operator_staff WHERE operator_id=?",operator);jdbc.update("DELETE FROM transport_operators WHERE id=?",operator);
        for(var point:f.locations()) jdbc.update("DELETE FROM locations WHERE id=?",point.getId());
        jdbc.update("DELETE FROM user_roles WHERE user_id=?",admin.id());jdbc.update("DELETE FROM users WHERE id=?",admin.id());
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
