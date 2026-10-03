package com.busgo;

import static org.assertj.core.api.Assertions.*;
import com.busgo.booking.*;
import com.busgo.booking.AssistedBookingDtos.*;
import com.busgo.common.security.CurrentUser;
import com.busgo.payment.entity.PaymentMethod;
import com.busgo.user.entity.RoleCode;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@SpringBootTest
@ActiveProfiles("dev")
class M16BConcurrencyIT extends M16ATestSupport {
    @Autowired TransactionTemplate transactions;
    @Autowired com.busgo.operations.OperationsService ops;
    @Autowired com.busgo.trip.operations.OperatorTripOperationsService lifecycle;
    private final List<Long> extraTrips = new ArrayList<>();
    private final List<Long> extraBuses = new ArrayList<>();
    private Fixture fixture;
    private CurrentUser admin;
    private final List<Long> userIds = new ArrayList<>();
    @BeforeEach void setup() {
        fixture = transactions.execute(s -> fixture());
        admin = transactions.execute(s -> actor(fixture, RoleCode.OPERATOR_ADMIN));
        userIds.add(admin.id()); authenticate(admin);
    }
    @Override void authenticate(CurrentUser actor) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,
                null, actor.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList()));
    }
    @AfterEach
    void cleanOwnedFixture() {
        if (fixture != null) {
            Long tripId = fixture.trip().getId();
            for(long extra:extraTrips) { jdbc.update("DELETE FROM operational_history WHERE trip_id=?",extra); jdbc.update("DELETE FROM trip_crew_assignments WHERE trip_id=?",extra); jdbc.update("DELETE FROM trips WHERE id=?",extra); }
            for(long bus:extraBuses) jdbc.update("DELETE FROM buses WHERE id=?",bus);
            M16BFixtures.clear(jdbc,fixture.operator().getId(),tripId);
            jdbc.update("DELETE tb FROM ticket_boarding tb JOIN tickets tk ON tk.id=tb.ticket_id JOIN bookings b ON b.id=tk.booking_id WHERE b.trip_id=?", tripId);
            jdbc.update("DELETE ticket FROM tickets ticket JOIN bookings booking ON booking.id=ticket.booking_id WHERE booking.trip_id=?", tripId);
            jdbc.update("DELETE history FROM booking_status_history history JOIN bookings booking ON booking.id=history.booking_id WHERE booking.trip_id=?", tripId);
            jdbc.update("DELETE payment FROM payments payment JOIN bookings booking ON booking.id=payment.booking_id WHERE booking.trip_id=?", tripId);
            jdbc.update("DELETE FROM trip_seat_segment_inventory WHERE trip_seat_id IN (SELECT id FROM trip_seats WHERE trip_id=?)", tripId);
            jdbc.update("DELETE item FROM booking_items item JOIN bookings booking ON booking.id=item.booking_id WHERE booking.trip_id=?", tripId);
            jdbc.update("DELETE FROM bookings WHERE trip_id=?", tripId);
            jdbc.update("DELETE FROM trip_seats WHERE trip_id=?", tripId);
            jdbc.update("DELETE FROM trip_segments WHERE trip_id=?", tripId);
            jdbc.update("DELETE FROM trip_stops WHERE trip_id=?", tripId);
            jdbc.update("DELETE FROM trips WHERE id=?", tripId);
            jdbc.update("DELETE FROM operator_route_fares WHERE operator_route_id=?", fixture.operatorRoute().getId());
            jdbc.update("DELETE FROM buses WHERE id=?", fixture.bus().getId());
            jdbc.update("DELETE FROM seat_templates WHERE bus_type_id=?", fixture.busType().getId());
            jdbc.update("DELETE FROM bus_types WHERE id=?", fixture.busType().getId());
            jdbc.update("DELETE FROM operator_routes WHERE id=?", fixture.operatorRoute().getId());
            jdbc.update("DELETE FROM route_stops WHERE route_id=?", fixture.route().getId());
            jdbc.update("DELETE FROM routes WHERE id=?", fixture.route().getId());
            jdbc.update("DELETE FROM operator_staff WHERE operator_id=?", fixture.operator().getId());
            jdbc.update("DELETE FROM transport_operators WHERE id=?", fixture.operator().getId());
            for (var location : fixture.locations()) jdbc.update("DELETE FROM locations WHERE id=?", location.getId());
        }
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM refresh_tokens WHERE user_id=?", userId);
            jdbc.update("DELETE FROM user_roles WHERE user_id=?", userId);
            jdbc.update("DELETE FROM users WHERE id=?", userId);
        }
    }


    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    private com.busgo.operations.OperationsDtos.Employee employee() {
        return ops.saveEmployee(admin,null,new com.busgo.operations.OperationsDtos.EmployeeInput("DRIVER","Driver","0901234567",com.busgo.operations.OperationsDtos.EmployeeStatus.ACTIVE,Set.of(com.busgo.operations.OperationsDtos.Capability.DRIVER),"LICENCE","DEMO",java.time.LocalDate.of(2099,12,31),null));
    }
    private com.busgo.operations.OperationsDtos.CrewReplacement assignment(long employee) {
        return new com.busgo.operations.OperationsDtos.CrewReplacement(List.of(new com.busgo.operations.OperationsDtos.CrewInput(employee,com.busgo.operations.OperationsDtos.Capability.DRIVER)));
    }
    private long secondTrip(boolean backToBack) {
        // Different buses ensure the employee lock, rather than a shared bus lock, protects the race.
        long bus=transactions.execute(s->{
            var value=new com.busgo.fleet.entity.Bus(); value.setOperator(fixture.operator()); value.setBusType(fixture.busType());
            value.setLicensePlate("R-"+UUID.randomUUID().toString().substring(0,20)); value.setStatus(com.busgo.fleet.entity.BusStatus.AVAILABLE);
            return buses.saveAndFlush(value).getId();
        }); extraBuses.add(bus);
        jdbc.update("INSERT INTO trips(operator_route_id,bus_id,departure_time,estimated_arrival_time,status,created_at,updated_at) SELECT operator_route_id,?,"+(backToBack?"estimated_arrival_time,DATE_ADD(estimated_arrival_time,INTERVAL 3 HOUR)":"departure_time,estimated_arrival_time")+",'SCHEDULED',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6) FROM trips WHERE id=?",bus,fixture.trip().getId());
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class); extraTrips.add(id); return id;
    }
    private String attempt(Callable<?> command) {
        authenticate(admin);
        try { command.call(); return "SUCCESS"; }
        catch(com.busgo.common.exception.BusinessException e) { return e.getCode(); }
        catch(Exception e) { throw new RuntimeException(e); }
    }
    private long paid(int from,int to) {
        var booking=assisted.create(admin,request(fixture,from,to,PaymentMethod.PAY_ON_BOARD));
        assisted.record(admin,booking.bookingId(),new RecordPayment(PaymentMethod.PAY_ON_BOARD,null));
        return jdbc.queryForObject("SELECT id FROM tickets WHERE booking_id=?",Long.class,booking.bookingId());
    }
    private void begin() { var e=employee(); ops.replaceCrew(admin,fixture.trip().getId(),assignment(e.id())); lifecycle.updateStatus(admin,fixture.trip().getId(),com.busgo.trip.entity.TripStatus.BOARDING); }
    private com.busgo.operations.OperationsDtos.PickupContext pickup(int index) { return new com.busgo.operations.OperationsDtos.PickupContext(fixture.stops().get(index).getId(),null); }
    private String act(long ticket,String command,int stop) { return attempt(()->ops.transition(admin,fixture.trip().getId(),ticket,command,pickup(stop))); }

    @Test void concurrentOverlappingAssignmentsHaveOneWinner() throws Exception {
        var e=employee(); long second=secondTrip(false);
        assertThat(race(()->attempt(()->ops.replaceCrew(admin,fixture.trip().getId(),assignment(e.id()))),()->attempt(()->ops.replaceCrew(admin,second,assignment(e.id()))))).containsExactlyInAnyOrder("SUCCESS","CREW_SCHEDULE_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_crew_assignments WHERE employee_id=? AND released_at IS NULL",Integer.class,e.id())).isOne();
    }
    @Test void backToBackAndDifferentEmployeesMayOverlap() {
        var e=employee(); long second=secondTrip(true);
        ops.replaceCrew(admin,fixture.trip().getId(),assignment(e.id())); ops.replaceCrew(admin,second,assignment(e.id()));
        var other=ops.saveEmployee(admin,null,new com.busgo.operations.OperationsDtos.EmployeeInput("OTHER","Other","0901234568",com.busgo.operations.OperationsDtos.EmployeeStatus.ACTIVE,Set.of(com.busgo.operations.OperationsDtos.Capability.DRIVER),"OTHER","DEMO",java.time.LocalDate.of(2099,12,31),null));
        long overlapping=secondTrip(false); ops.replaceCrew(admin,overlapping,assignment(other.id()));
    }
    @Test void inactiveDeactivationAndReadinessRulesAreEnforced() {
        assertThat(attempt(()->lifecycle.updateStatus(admin,fixture.trip().getId(),com.busgo.trip.entity.TripStatus.BOARDING))).isEqualTo("CREW_NOT_READY");
        var e=employee(); ops.replaceCrew(admin,fixture.trip().getId(),assignment(e.id()));
        jdbc.update("UPDATE driver_profiles SET licence_expiry_date='2000-01-01' WHERE employee_id=?",e.id());
        assertThat(attempt(()->lifecycle.updateStatus(admin,fixture.trip().getId(),com.busgo.trip.entity.TripStatus.BOARDING))).isEqualTo("CREW_NOT_READY");
        jdbc.update("UPDATE driver_profiles SET licence_expiry_date='2099-12-31' WHERE employee_id=?",e.id());
        assertThat(attempt(()->ops.saveEmployee(admin,e.id(),new com.busgo.operations.OperationsDtos.EmployeeInput(e.employeeCode(),e.fullName(),e.phone(),com.busgo.operations.OperationsDtos.EmployeeStatus.INACTIVE,e.capabilities(),e.licenceNumber(),e.licenceClass(),e.licenceExpiryDate(),e.version())))).isEqualTo("EMPLOYEE_HAS_ASSIGNMENTS");
        ops.replaceCrew(admin,fixture.trip().getId(),new com.busgo.operations.OperationsDtos.CrewReplacement(List.of()));
        var inactive=ops.saveEmployee(admin,e.id(),new com.busgo.operations.OperationsDtos.EmployeeInput(e.employeeCode(),e.fullName(),e.phone(),com.busgo.operations.OperationsDtos.EmployeeStatus.INACTIVE,e.capabilities(),e.licenceNumber(),e.licenceClass(),e.licenceExpiryDate(),e.version()));
        assertThat(attempt(()->ops.replaceCrew(admin,fixture.trip().getId(),assignment(inactive.id())))).isEqualTo("EMPLOYEE_NOT_ELIGIBLE");
    }
    @Test void duplicateCheckInAndBoardAreIdempotentUnderConcurrency() throws Exception {
        long ticket=paid(0,2); begin();
        assertThat(race(()->act(ticket,"check-in",0),()->act(ticket,"check-in",0))).containsOnly("SUCCESS");
        assertThat(race(()->act(ticket,"board",0),()->act(ticket,"board",0))).containsOnly("SUCCESS");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operational_history WHERE entity_type='TICKET' AND entity_id=?",Integer.class,ticket)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM ticket_boarding WHERE ticket_id=?",String.class,ticket)).isEqualTo("BOARDED");
    }
    @Test void boardVersusNoShowHasOneTerminalWinner() throws Exception {
        long ticket=paid(0,2); begin(); act(ticket,"check-in",0);
        assertThat(race(()->act(ticket,"board",0),()->act(ticket,"no-show",0))).containsExactlyInAnyOrder("SUCCESS","INVALID_BOARDING_TRANSITION");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operational_history WHERE entity_type='TICKET' AND entity_id=? AND action IN ('BOARD','NO_SHOW')",Integer.class,ticket)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory WHERE booking_item_id IN (SELECT booking_item_id FROM tickets WHERE id=?) AND status='BOOKED'",Integer.class,ticket)).isEqualTo(2);
    }
    @Test void pickupClosureVersusBoardPreservesResolution() throws Exception {
        long ticket=paid(0,2); begin(); act(ticket,"check-in",0);
        var results=race(()->act(ticket,"board",0),()->attempt(()->ops.closePickup(admin,fixture.trip().getId(),fixture.stops().get(0).getId(),null)));
        assertThat(results.get(0)).isEqualTo("SUCCESS"); assertThat(results.get(1)).isIn("SUCCESS","PICKUP_UNRESOLVED");
        ops.closePickup(admin,fixture.trip().getId(),fixture.stops().get(0).getId(),null);
        assertThat(jdbc.queryForObject("SELECT status FROM ticket_boarding WHERE ticket_id=?",String.class,ticket)).isEqualTo("BOARDED");
    }
    @Test void completionCannotRacePastUnresolvedIntermediatePassenger() throws Exception {
        long ticket=paid(2,3); begin();
        ops.closePickup(admin,fixture.trip().getId(),fixture.stops().get(0).getId(),null);
        lifecycle.updateStatus(admin,fixture.trip().getId(),com.busgo.trip.entity.TripStatus.DEPARTED);
        assertThat(race(()->act(ticket,"direct-board",2),()->attempt(()->lifecycle.updateStatus(admin,fixture.trip().getId(),com.busgo.trip.entity.TripStatus.COMPLETED)))).containsExactlyInAnyOrder("SUCCESS","PICKUP_UNRESOLVED");
        for(int i=1;i<3;i++) ops.closePickup(admin,fixture.trip().getId(),fixture.stops().get(i).getId(),null);
        lifecycle.updateStatus(admin,fixture.trip().getId(),com.busgo.trip.entity.TripStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT actual_boarding_stop_id FROM ticket_boarding WHERE ticket_id=?",Long.class,ticket)).isEqualTo(fixture.stops().get(2).getId());
    }
    @Test void paymentRecordingVersusBoardingRequiresIssuedPaidTicket() throws Exception {
        var b=assisted.create(admin,request(fixture,2,3,PaymentMethod.PAY_ON_BOARD)); begin();
        ops.closePickup(admin,fixture.trip().getId(),fixture.stops().get(0).getId(),null);
        lifecycle.updateStatus(admin,fixture.trip().getId(),com.busgo.trip.entity.TripStatus.DEPARTED);
        var result=race(()->attempt(()->assisted.record(admin,b.bookingId(),new RecordPayment(PaymentMethod.PAY_ON_BOARD,null))),()->{
            var tickets=jdbc.queryForList("SELECT id FROM tickets WHERE booking_id=?",Long.class,b.bookingId());
            return act(tickets.isEmpty()?Long.MAX_VALUE:tickets.get(0),"direct-board",2);
        });
        assertThat(result.get(0)).isEqualTo("SUCCESS"); assertThat(result.get(1)).isIn("SUCCESS","TICKET_NOT_FOUND");
        long ticket=jdbc.queryForObject("SELECT id FROM tickets WHERE booking_id=?",Long.class,b.bookingId());
        assertThat(act(ticket,"direct-board",2)).isEqualTo("SUCCESS");
        assertThat(count("payments",b.bookingId())).isOne();
    }
    private long item(long booking) { return jdbc.queryForObject("SELECT id FROM booking_items WHERE booking_id=?",Long.class,booking); }
    private String absent(long item) { return attempt(()->ops.reservationNoShow(admin,fixture.trip().getId(),item,pickup(0))); }
    private String closeOrigin() { return attempt(()->ops.closePickup(admin,fixture.trip().getId(),fixture.stops().get(0).getId(),null)); }
    @Test void unpaidAbsencePreservesCommerceAndClosesPickup() {
        var b=assisted.create(admin,request(fixture,0,2,PaymentMethod.PAY_ON_BOARD)); long item=item(b.bookingId()); begin();
        var bookingBefore=jdbc.queryForMap("SELECT * FROM bookings WHERE id=?",b.bookingId());
        var inventoryBefore=jdbc.queryForList("SELECT * FROM trip_seat_segment_inventory WHERE booking_item_id=? ORDER BY id",item);
        var historyBefore=jdbc.queryForList("SELECT * FROM booking_status_history WHERE booking_id=?",b.bookingId());
        assertThat(closeOrigin()).isEqualTo("PICKUP_UNRESOLVED");
        for(String command:List.of("check-in","board","direct-board")) assertThat(act(item,command,0)).isEqualTo("TICKET_NOT_FOUND");
        assertThat(absent(item)).isEqualTo("SUCCESS");
        assertThat(count("payments",b.bookingId())).isZero(); assertThat(count("tickets",b.bookingId())).isZero();
        assertThat(jdbc.queryForMap("SELECT * FROM bookings WHERE id=?",b.bookingId())).isEqualTo(bookingBefore);
        assertThat(jdbc.queryForList("SELECT * FROM trip_seat_segment_inventory WHERE booking_item_id=? ORDER BY id",item)).isEqualTo(inventoryBefore);
        assertThat(jdbc.queryForList("SELECT * FROM booking_status_history WHERE booking_id=?",b.bookingId())).isEqualTo(historyBefore);
        assertThat(ops.attendance(admin,fixture.trip().getId()).get(0)).containsEntry("boardingStatus","NO_SHOW").containsEntry("ticketId",null);
        assertThat(attempt(()->assisted.record(admin,b.bookingId(),new RecordPayment(PaymentMethod.PAY_ON_BOARD,null)))).isEqualTo("BOOKING_ATTENDANCE_TERMINAL");
        assertThat(closeOrigin()).isEqualTo("SUCCESS");
        assertThat(absent(item)).isEqualTo("SUCCESS");
        lifecycle.updateStatus(admin,fixture.trip().getId(),com.busgo.trip.entity.TripStatus.DEPARTED);
        for(int i=1;i<3;i++) ops.closePickup(admin,fixture.trip().getId(),fixture.stops().get(i).getId(),null);
        lifecycle.updateStatus(admin,fixture.trip().getId(),com.busgo.trip.entity.TripStatus.COMPLETED);
    }
    @Test void paidAndUnpaidPassengersResolveSamePickup() {
        long ticket=paid(0,2);
        var b=assisted.create(admin,new CreateRequest(fixture.trip().getId(),fixture.locations().get(0).getId(),fixture.locations().get(2).getId(),List.of(fixture.seats().get(1).getId()),"Absent","0901234567",null,PaymentMethod.PAY_ON_BOARD));
        begin(); assertThat(act(ticket,"check-in",0)).isEqualTo("SUCCESS"); assertThat(act(ticket,"board",0)).isEqualTo("SUCCESS");
        assertThat(closeOrigin()).isEqualTo("PICKUP_UNRESOLVED"); assertThat(absent(item(b.bookingId()))).isEqualTo("SUCCESS"); assertThat(closeOrigin()).isEqualTo("SUCCESS");
    }
    @Test void duplicateUnpaidAbsenceIsIdempotentUnderConcurrency() throws Exception {
        var b=assisted.create(admin,request(fixture,0,2,PaymentMethod.PAY_ON_BOARD)); long item=item(b.bookingId()); begin();
        assertThat(race(()->absent(item),()->absent(item))).containsOnly("SUCCESS");
        var before=jdbc.queryForMap("SELECT * FROM ticket_boarding WHERE booking_item_id=?",item); absent(item);
        assertThat(jdbc.queryForMap("SELECT * FROM ticket_boarding WHERE booking_item_id=?",item)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operational_history WHERE entity_type='BOOKING_ITEM' AND entity_id=? AND action='NO_SHOW'",Integer.class,item)).isOne();
    }
    @Test void unpaidAbsenceAndClosureSerializeSafely() throws Exception {
        var b=assisted.create(admin,request(fixture,0,2,PaymentMethod.PAY_ON_BOARD)); long item=item(b.bookingId()); begin();
        var result=race(()->absent(item),this::closeOrigin);
        assertThat(result.get(0)).isEqualTo("SUCCESS"); assertThat(result.get(1)).isIn("SUCCESS","PICKUP_UNRESOLVED");
        assertThat(closeOrigin()).isEqualTo("SUCCESS"); assertThat(count("tickets",b.bookingId())).isZero();
    }
    @Test void unpaidAbsenceAndPaymentHaveDefinedSerialOutcome() throws Exception {
        var b=assisted.create(admin,request(fixture,0,2,PaymentMethod.PAY_ON_BOARD)); long item=item(b.bookingId()); begin();
        var result=race(()->absent(item),()->attempt(()->assisted.record(admin,b.bookingId(),new RecordPayment(PaymentMethod.PAY_ON_BOARD,null))));
        assertThat(result.get(0)).isEqualTo("SUCCESS"); assertThat(result.get(1)).isIn("SUCCESS","BOOKING_ATTENDANCE_TERMINAL");
        assertThat(count("tickets",b.bookingId())).isEqualTo(result.get(1).equals("SUCCESS")?1:0);
        assertThat(jdbc.queryForObject("SELECT status FROM ticket_boarding WHERE booking_item_id=?",String.class,item)).isEqualTo("NO_SHOW");
    }
    @Test void unpaidNoShowRejectsOtherMethodsWrongStopAndForeignItem() {
        var b=assisted.create(admin,request(fixture,0,2,PaymentMethod.QR_TRANSFER)); begin();
        assertThat(absent(item(b.bookingId()))).isEqualTo("RESERVATION_NOT_ELIGIBLE");
        assertThat(attempt(()->ops.reservationNoShow(admin,fixture.trip().getId(),item(b.bookingId()),pickup(1)))).isEqualTo("WRONG_PICKUP_STOP");
        assertThat(absent(Long.MAX_VALUE)).isEqualTo("BOOKING_ITEM_NOT_FOUND");
    }
    @Test void oneNoShowBlocksFullBookingPaymentButRemainingItemsStillNeedResolution() {
        var b=assisted.create(admin,new CreateRequest(fixture.trip().getId(),fixture.locations().get(0).getId(),fixture.locations().get(2).getId(),fixture.seats().stream().map(s->s.getId()).toList(),"Absent group","0901234567",null,PaymentMethod.PAY_ON_BOARD));
        var items=jdbc.queryForList("SELECT id FROM booking_items WHERE booking_id=? ORDER BY id",Long.class,b.bookingId()); begin();
        assertThat(absent(items.get(0))).isEqualTo("SUCCESS"); assertThat(closeOrigin()).isEqualTo("PICKUP_UNRESOLVED");
        assertThat(ops.attendance(admin,fixture.trip().getId())).allSatisfy(row->assertThat(row.get("paymentBlocked")).isEqualTo(true));
        assertThat(attempt(()->assisted.record(admin,b.bookingId(),new RecordPayment(PaymentMethod.PAY_ON_BOARD,null)))).isEqualTo("BOOKING_ATTENDANCE_TERMINAL");
        assertThat(absent(items.get(1))).isEqualTo("SUCCESS"); assertThat(closeOrigin()).isEqualTo("SUCCESS");
        assertThat(count("tickets",b.bookingId())).isZero(); assertThat(count("payments",b.bookingId())).isZero();
    }
    private List<String> race(Callable<String> first, Callable<String> second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2); ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> a = executor.submit(() -> { barrier.await(10, TimeUnit.SECONDS); try { return first.call(); } finally { SecurityContextHolder.clearContext(); } });
            Future<String> b = executor.submit(() -> { barrier.await(10, TimeUnit.SECONDS); try { return second.call(); } finally { SecurityContextHolder.clearContext(); } });
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }
}

