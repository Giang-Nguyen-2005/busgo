package com.busgo;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.busgo.booking.*;
import com.busgo.booking.ModificationDtos.*;
import com.busgo.common.security.CurrentUser;
import com.busgo.payment.PaymentTicketService;
import com.busgo.payment.entity.PaymentMethod;
import com.busgo.trip.entity.*;
import com.busgo.user.entity.RoleCode;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class M20ModificationIT extends M20Support {
    @Autowired MockMvc mvc;
    @Autowired CancellationService cancellations;
    @AfterEach void clear() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }

    @ParameterizedTest @ValueSource(ints={1,2,3})
    void partialSeatChangesReplaceOnlyAffectedTickets(int changed) {
        var f=expanded(); var owner=customer("m20-partial"); authenticate(owner.user());
        var b=web(f,owner,0,1,2); paymentService.confirm(owner.user(),b.bookingId());
        var original=jdbc.queryForList("SELECT id FROM tickets WHERE booking_id=? ORDER BY booking_item_id",Long.class,b.bookingId());
        var ids=itemIds(b.bookingId()); var selections=new ArrayList<Selection>();
        for(int i=0;i<changed;i++) selections.add(new Selection(ids.get(i),f.seats().get(3+i).getId()));
        var req=new Request(Type.SEAT_CHANGE,f.trip().getId(),selections);
        var q=modifications.quote(owner.user(),b.bookingId(),false,req); assertThat(q.money().fareDelta()).isZero();
        var m=modifications.create(owner.user(),b.bookingId(),false,req);
        assertThat(bookingService.detail(owner.user(),b.bookingId()).seats().stream().map(s->s.seatCode())).containsExactly("A01","A02","A03");
        var done=modifications.confirm(owner.user(),b.bookingId(),false,m.id());
        assertThat(done.status()).isEqualTo("COMPLETED"); assertThat(done.quote().bookingCode()).isEqualTo(b.bookingCode());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE booking_id=? AND replaced=TRUE AND status='VOID'",Integer.class,b.bookingId())).isEqualTo(changed);
        for(int i=changed;i<3;i++) assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE id=?",String.class,original.get(i))).isEqualTo("VALID");
        assertThat(paymentService.ticket(owner.user(),b.bookingId()).tickets()).hasSize(3);
        assertThat(paymentService.confirm(owner.user(),b.bookingId()).bookingCode()).isEqualTo(b.bookingCode());
        assertThat(modifications.confirm(owner.user(),b.bookingId(),false,m.id()).status()).isEqualTo("COMPLETED");
        cancellations.customerCancel(owner.user(),b.bookingId(),new CancellationDtos.Request(null));
        assertThat(modifications.history(owner.user(),b.bookingId(),false)).hasSize(1);
        assertThat(paymentService.ticket(owner.user(),b.bookingId()).tickets()).hasSize(3).allMatch(t->t.status().equals("VOID"));
    }
    @ParameterizedTest @ValueSource(strings={"100.25","200.00","300.75"})
    void paidTripChangesKeepHistoricalPrincipalAndRefundRemainingOnCancellation(String fare) {
        var f=expanded(); var owner=customer("m20-paid"); authenticate(owner.user()); var b=web(f,owner,0);
        paymentService.confirm(owner.user(),b.bookingId()); var t=targetTrip(f);
        jdbc.update("UPDATE operator_route_fares SET price=? WHERE operator_route_id=?",new BigDecimal(fare),f.operatorRoute().getId()); em.clear();
        var req=tripRequest(b.bookingId(),t); var m=modifications.create(owner.user(),b.bookingId(),false,req);
        assertThat(m.quote().money().currentTotal()).isEqualByComparingTo("200");
        assertThat(m.quote().money().fareDelta()).isEqualByComparingTo(new BigDecimal(fare).subtract(new BigDecimal("200")));
        assertThat(modifications.confirm(owner.user(),b.bookingId(),false,m.id()).status()).isEqualTo("COMPLETED");
        assertThat(bookingService.detail(owner.user(),b.bookingId()).tripId()).isEqualTo(t.getId());
        assertThat(jdbc.queryForObject("SELECT amount FROM payments WHERE booking_id=? AND purpose='BOOKING'",BigDecimal.class,b.bookingId())).isEqualByComparingTo("200");
        assertThat(paymentService.ticket(owner.user(),b.bookingId()).tickets()).hasSize(1);
        cancellations.customerCancel(owner.user(),b.bookingId(),new CancellationDtos.Request(null));
        BigDecimal gross=jdbc.queryForObject("SELECT SUM(amount) FROM payments WHERE booking_id=?",BigDecimal.class,b.bookingId());
        assertThat(jdbc.queryForObject("SELECT SUM(r.amount) FROM refunds r JOIN payments p ON p.id=r.payment_id WHERE p.booking_id=?",BigDecimal.class,b.bookingId())).isEqualByComparingTo(gross);
    }
    @ParameterizedTest @ValueSource(strings={"100.25","300.75"})
    void unpaidPhoneTripChangeUpdatesWholeAmountWithoutCashAndRevokesOldLink(String fare) {
        for(var method:List.of(PaymentMethod.PAY_ON_BOARD,PaymentMethod.QR_TRANSFER)) {
            var f=expanded(); var admin=actor(f,RoleCode.OPERATOR_ADMIN); authenticate(admin);
            var b=assisted.create(admin,request(f,0,2,method)); String old=method==PaymentMethod.QR_TRANSFER?token(assisted.issueLink(admin,b.bookingId())):null;
            var t=targetTrip(f); jdbc.update("UPDATE operator_route_fares SET price=? WHERE operator_route_id=?",new BigDecimal(fare),f.operatorRoute().getId()); em.clear();
            var m=modifications.create(admin,b.bookingId(),true,tripRequest(b.bookingId(),t));
            assertThat(m.quote().money().collectionRequired()).isZero(); assertThat(m.quote().money().refundRequired()).isZero();
            assertThat(m.quote().money().newAmountDue()).isEqualByComparingTo(fare);
            modifications.confirm(admin,b.bookingId(),true,m.id());
            assertThat(count("payments",b.bookingId())).isZero(); assertThat(count("tickets",b.bookingId())).isZero();
            assertThat(jdbc.queryForObject("SELECT contact_name FROM bookings WHERE id=?",String.class,b.bookingId())).isEqualTo("Caller");
            if(old!=null) {
                assertThatThrownBy(()->assisted.publicContext(old)).isInstanceOf(com.busgo.common.exception.ResourceNotFoundException.class);
                assertThat(assisted.publicContext(token(assisted.issueLink(admin,b.bookingId()))).amount()).isEqualByComparingTo(fare);
            } else assertThat(jdbc.queryForObject("SELECT payment_due_at FROM bookings WHERE id=?",Object.class,b.bookingId())).isNull();
        }
    }
    @Test void cancelledAndExpiredAttemptsPreserveSourceAndToken() {
        var f=expanded(); var admin=actor(f,RoleCode.OPERATOR_ADMIN); authenticate(admin); var b=assisted.create(admin,request(f,0,2,PaymentMethod.QR_TRANSFER));
        String token=token(assisted.issueLink(admin,b.bookingId())); var req=seatRequest(b.bookingId(),f,3);
        var m=modifications.create(admin,b.bookingId(),true,req); modifications.cancel(admin,b.bookingId(),true,m.id());
        assertThat(assisted.publicContext(token).bookingCode()).isEqualTo(b.bookingCode());
        m=modifications.create(admin,b.bookingId(),true,req);
        jdbc.update("UPDATE booking_modifications SET expires_at=? WHERE id=?",com.busgo.common.time.JpaJdbcTime.parameter(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1)),m.id());
        assertThat(modifications.expire(m.id())).isTrue(); assertThat(modifications.expire(m.id())).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory WHERE trip_seat_id=? AND status='HELD'",Integer.class,f.seats().get(3).getId())).isZero();
        assertThat(assisted.publicContext(token).seats()).containsExactly("A01");
    }
    @ParameterizedTest @ValueSource(strings={"CHECKED_IN","BOARDED","NO_SHOW"})
    void attendanceBlocksBothActors(String status) {
        var f=expanded(); var owner=customer("m20-attendance"); authenticate(owner.user()); var b=web(f,owner,0); paymentService.confirm(owner.user(),b.bookingId());
        jdbc.update("UPDATE ticket_boarding a JOIN booking_items i ON i.id=a.booking_item_id SET a.status=? WHERE i.booking_id=?",status,b.bookingId());
        assertThat(modifications.eligibility(owner.user(),b.bookingId(),false).seatChange().reasonCode()).isEqualTo("ATTENDANCE_CONFLICT");
        var admin=actor(f,RoleCode.OPERATOR_ADMIN); authenticate(admin);
        assertThat(modifications.eligibility(admin,b.bookingId(),true).tripChange().allowed()).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"BOARDING","DEPARTED","COMPLETED"})
    void sourceLifecycleBlocksModification(String status) {
        var f=expanded(); var owner=customer("m20-state"); var b=web(f,owner,0);
        jdbc.update("UPDATE trips SET status=? WHERE id=?",status,f.trip().getId()); em.clear();
        assertThat(modifications.eligibility(owner.user(),b.bookingId(),false).seatChange().allowed()).isFalse();
    }
    @Test void customerCutoffIsAuthoritative() {
        var f=expanded(); var owner=customer("m20-cutoff"); var b=web(f,owner,0);
        jdbc.update("UPDATE trip_stops SET planned_departure_time=? WHERE id=?",com.busgo.common.time.JpaJdbcTime.parameter(LocalDateTime.now(ZoneOffset.UTC).plusHours(5)),f.stops().get(0).getId()); em.clear();
        assertThat(modifications.eligibility(owner.user(),b.bookingId(),false).seatChange().reasonCode()).isEqualTo("CUSTOMER_CUTOFF");
    }
    @Test void duplicateNoopAndInsufficientTripSeatsRejected() {
        var f=expanded(); var owner=customer("m20-invalid"); var b=web(f,owner,0,1); var ids=itemIds(b.bookingId()); var t=targetTrip(f);
        assertThatThrownBy(()->modifications.quote(owner.user(),b.bookingId(),false,new Request(Type.SEAT_CHANGE,f.trip().getId(),List.of(new Selection(ids.get(0),f.seats().get(0).getId()))))).isInstanceOf(com.busgo.common.exception.BusinessException.class);
        assertThatThrownBy(()->modifications.quote(owner.user(),b.bookingId(),false,new Request(Type.SEAT_CHANGE,f.trip().getId(),List.of(new Selection(ids.get(0),f.seats().get(3).getId()),new Selection(ids.get(1),f.seats().get(3).getId()))))).isInstanceOf(com.busgo.common.exception.BusinessException.class);
        assertThatThrownBy(()->modifications.quote(owner.user(),b.bookingId(),false,new Request(Type.TRIP_CHANGE,t.getId(),List.of(new Selection(ids.get(0),targetSeats(t).get(0)))))).isInstanceOf(com.busgo.common.exception.BusinessException.class);
    }
    @Test void occupiedHeldBlockedAndForeignTargetDenied() {
        var f=expanded(); var owner=customer("m20-conflict"); var b=web(f,owner,0); var other=customer("m20-other"); web(f,other,1); hold(other,f,0,2,2);
        jdbc.update("UPDATE trip_seat_segment_inventory SET status='BLOCKED' WHERE trip_seat_id=?",f.seats().get(3).getId());
        for(int seat:List.of(1,2,3)) assertThatThrownBy(()->modifications.create(owner.user(),b.bookingId(),false,seatRequest(b.bookingId(),f,seat))).isInstanceOf(com.busgo.common.exception.BusinessException.class);
        var foreign=expanded(); assertThatThrownBy(()->modifications.quote(owner.user(),b.bookingId(),false,tripRequest(b.bookingId(),foreign.trip()))).isInstanceOf(com.busgo.common.exception.ResourceNotFoundException.class);
    }
    @Test void protectedApisConcealOwnershipAndRejectWrongRoles() throws Exception {
        var f=expanded(); var owner=customer("m20-security"); var other=customer("m20-foreign"); var b=web(f,owner,0);
        String path="/api/v1/bookings/"+b.bookingId()+"/modification-eligibility";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization","Bearer "+other.token())).andExpect(status().isNotFound());
        var staff=actor(f,RoleCode.OPERATOR_STAFF); var system=actor(f,RoleCode.SYSTEM_ADMIN); var foreign=actor(fixture(),RoleCode.OPERATOR_ADMIN);
        for(var actor:List.of(staff,system)) mvc.perform(post("/api/v1/operator/bookings/"+b.bookingId()+"/modifications").header("Authorization",bearer(actor)).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/operator/bookings/"+b.bookingId()+"/modification-eligibility").header("Authorization",bearer(foreign))).andExpect(status().isNotFound());
    }

    @Test void alternativeDepartureMatchesAuthoritativeJourney() {
        var f=expanded(); var owner=customer("m20-time"); var b=web(f,owner,0); var target=targetTrip(f);
        var departure=LocalDateTime.now(ZoneOffset.UTC).plusDays(2).withNano(0);
        jdbc.update("UPDATE trip_stops SET planned_departure_time=? WHERE trip_id=? AND stop_order=1",com.busgo.common.time.JpaJdbcTime.parameter(departure),target.getId()); em.clear();
        var alternative=modifications.alternatives(owner.user(),b.bookingId(),false).stream().filter(t->((Number)t.get("tripId")).longValue()==target.getId()).findFirst().orElseThrow();
        assertThat(alternative.get("departureTime")).isEqualTo(com.busgo.common.time.BusGoTime.api(departure));
        assertThat(modifications.seatMap(owner.user(),b.bookingId(),false,target.getId()).pickup().departureTime()).isEqualTo(alternative.get("departureTime"));
    }

    @Test void repeatedChangesPreservePaymentsTicketsHistoryAndCancelNetBalance() {
        var f=expanded(); var owner=customer("m20-repeat"); authenticate(owner.user()); var b=web(f,owner,0,1,2); paymentService.confirm(owner.user(),b.bookingId());
        for(String fare:List.of("300.25","100.50","250.75")) {
            var target=targetTrip(f); jdbc.update("UPDATE operator_route_fares SET price=? WHERE operator_route_id=?",new BigDecimal(fare),f.operatorRoute().getId()); em.clear();
            var m=modifications.create(owner.user(),b.bookingId(),false,tripRequest(b.bookingId(),target));
            modifications.confirm(owner.user(),b.bookingId(),false,m.id());
            assertThat(paymentService.ticket(owner.user(),b.bookingId()).tickets()).hasSize(3);
            assertThat(paymentService.confirm(owner.user(),b.bookingId()).bookingCode()).isEqualTo(b.bookingCode());
        }
        assertThat(modifications.history(owner.user(),b.bookingId(),false)).hasSize(3);
        assertThat(jdbc.queryForObject("SELECT amount FROM payments WHERE booking_id=? AND purpose='BOOKING'",BigDecimal.class,b.bookingId())).isEqualByComparingTo("600");
        cancellations.customerCancel(owner.user(),b.bookingId(),new CancellationDtos.Request(null));
        assertThat(paymentService.ticket(owner.user(),b.bookingId()).tickets()).hasSize(3).allMatch(t->t.status().equals("VOID"));
        assertThat(jdbc.queryForObject("SELECT (SELECT SUM(amount) FROM payments WHERE booking_id=?)-(SELECT SUM(r.amount) FROM refunds r JOIN payments p ON p.id=r.payment_id WHERE p.booking_id=?)",BigDecimal.class,b.bookingId(),b.bookingId())).isZero();
        assertThat(modifications.history(owner.user(),b.bookingId(),false)).allMatch(h->h.status().equals("COMPLETED"));
    }

    @ParameterizedTest @ValueSource(strings={"BOARDING","DEPARTED","COMPLETED","CANCELLED"})
    void targetLifecycleDenied(String status) {
        var f=expanded(); var owner=customer("m20-target"); var b=web(f,owner,0); var target=targetTrip(f);
        jdbc.update("UPDATE trips SET status=? WHERE id=?",status,target.getId()); em.clear();
        assertThatThrownBy(()->modifications.create(owner.user(),b.bookingId(),false,tripRequest(b.bookingId(),target))).isInstanceOf(com.busgo.common.exception.BusinessException.class);
    }

    @Test void targetPickupPermissionAndOrderAreRevalidated() {
        var f=expanded(); var owner=customer("m20-order"); var b=web(f,owner,0); var target=targetTrip(f);
        jdbc.update("UPDATE trip_stops SET allow_pickup=FALSE WHERE trip_id=? AND stop_order=1",target.getId()); em.clear();
        assertThatThrownBy(()->modifications.quote(owner.user(),b.bookingId(),false,tripRequest(b.bookingId(),target))).isInstanceOf(com.busgo.common.exception.BusinessException.class);
    }

    @Test void onlyActualJourneySegmentsBlockTargetSeat() {
        var f=expanded(); var owner=customer("m20-segment"); var b=web(f,owner,0);
        jdbc.update("UPDATE trip_seat_segment_inventory SET status='BLOCKED' WHERE trip_seat_id=? AND trip_segment_id=?",f.seats().get(3).getId(),f.segments().get(2).getId());
        assertThat(modifications.create(owner.user(),b.bookingId(),false,seatRequest(b.bookingId(),f,3)).status()).isEqualTo("HELD");
    }

    @Test void customerCannotAccessPhoneBookingAndStaffReadIsExplicitlyReadOnly() throws Exception {
        var f=expanded(); var admin=actor(f,RoleCode.OPERATOR_ADMIN); authenticate(admin);
        var b=assisted.create(admin,request(f,0,2,PaymentMethod.PAY_ON_BOARD)); var customer=customer("m20-phone");
        mvc.perform(get("/api/v1/bookings/"+b.bookingId()+"/modification-eligibility").header("Authorization","Bearer "+customer.token())).andExpect(status().isNotFound());
        var staff=actor(f,RoleCode.OPERATOR_STAFF);
        mvc.perform(get("/api/v1/operator/bookings/"+b.bookingId()+"/modification-eligibility").header("Authorization",bearer(staff))).andExpect(status().isOk()).andExpect(jsonPath("data.seatChange.reasonCode").value("ADMIN_REQUIRED"));
    }
}

abstract class M20Support extends M16ATestSupport {
    @Autowired ModificationService modifications;
    @Autowired PaymentTicketService paymentService;
    Fixture expanded() {
        var f=fixture(); var seats=new ArrayList<>(f.seats());
        for(int n=3;n<=6;n++) {
            TripSeat seat=new TripSeat(); seat.setTrip(f.trip()); seat.setSeatCode("A0"+n); seat.setRow(n); seat.setColumn(1); seat.setFloor(1); seat.setSeatType(com.busgo.fleet.entity.SeatType.STANDARD); tripSeats.saveAndFlush(seat); seats.add(seat);
            for(var segment:f.segments()) { var row=new TripSeatSegmentInventory(); row.setTripSeat(seat); row.setTripSegment(segment); row.setStatus(InventoryStatus.AVAILABLE); inventory.saveAndFlush(row); }
        }
        return new Fixture(f.locations(),f.trip(),f.stops(),f.segments(),seats,f.operatorRoute(),f.operator(),f.route(),f.bus(),f.busType(),f.fares());
    }
    Trip targetTrip(Fixture f) {
        Trip t=new Trip(); t.setOperatorRoute(f.operatorRoute()); t.setBus(f.bus()); t.setDepartureTime(f.trip().getDepartureTime().plusDays(1)); t.setEstimatedArrivalTime(t.getDepartureTime().plusHours(3)); t.setStatus(TripStatus.SCHEDULED); trips.saveAndFlush(t);
        var stops=new ArrayList<TripStop>();
        for(var old:f.stops()) { var s=new TripStop(); s.setTrip(t); s.setSourceRouteStop(old.getSourceRouteStop()); s.setLocation(old.getLocation()); s.setStopOrder(old.getStopOrder()); s.setAllowPickup(old.isAllowPickup()); s.setAllowDropoff(old.isAllowDropoff()); s.setStatus(old.getStatus()); s.setPlannedArrivalTime(old.getPlannedArrivalTime()==null?null:old.getPlannedArrivalTime().plusDays(1)); s.setPlannedDepartureTime(old.getPlannedDepartureTime()==null?null:old.getPlannedDepartureTime().plusDays(1)); stops.add(tripStops.saveAndFlush(s)); }
        var segs=new ArrayList<TripSegment>();
        for(int n=0;n<3;n++) { var s=new TripSegment(); s.setTrip(t); s.setSegmentOrder(n+1); s.setFromTripStop(stops.get(n)); s.setToTripStop(stops.get(n+1)); segs.add(segments.saveAndFlush(s)); }
        for(var old:f.seats()) { var s=new TripSeat(); s.setTrip(t); s.setSeatCode(old.getSeatCode()); s.setRow(old.getRow()); s.setColumn(old.getColumn()); s.setFloor(old.getFloor()); s.setSeatType(old.getSeatType()); tripSeats.saveAndFlush(s); for(var seg:segs) { var row=new TripSeatSegmentInventory(); row.setTripSeat(s); row.setTripSegment(seg); row.setStatus(InventoryStatus.AVAILABLE); inventory.saveAndFlush(row); } }
        return t;
    }
    BookingDtos.BookingResponse web(Fixture f,UserAuth owner,int... seats) { return bookingService.create(owner.user(),new BookingDtos.CreateBookingRequest(hold(owner,f,0,2,seats).holdToken(),"M20 Customer","0901234567","m20@example.test")); }
    List<Long> itemIds(long id) { return jdbc.queryForList("SELECT id FROM booking_items WHERE booking_id=? ORDER BY id",Long.class,id); }
    List<Long> targetSeats(Trip trip) { return jdbc.queryForList("SELECT id FROM trip_seats WHERE trip_id=? ORDER BY id",Long.class,trip.getId()); }
    Request seatRequest(long id,Fixture f,int seat) { return new Request(Type.SEAT_CHANGE,f.trip().getId(),List.of(new Selection(itemIds(id).get(0),f.seats().get(seat).getId()))); }
    Request tripRequest(long id,Trip t) { var ids=itemIds(id); var seats=targetSeats(t); var selections=new ArrayList<Selection>(); for(int i=0;i<ids.size();i++) selections.add(new Selection(ids.get(i),seats.get(i))); return new Request(Type.TRIP_CHANGE,t.getId(),selections); }
}
