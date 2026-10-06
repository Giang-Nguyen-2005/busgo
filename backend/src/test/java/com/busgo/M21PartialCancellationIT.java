package com.busgo;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.busgo.booking.*;
import com.busgo.booking.PartialCancellationDtos.Request;
import com.busgo.common.exception.BusinessException;
import com.busgo.payment.entity.PaymentMethod;
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
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev") @Transactional
class M21PartialCancellationIT extends M20Support {
 @Autowired PartialCancellationService partial;
 @Autowired CancellationService cancellations;
 @Autowired com.busgo.operations.OperationsService operations;
 @Autowired MockMvc mvc;
 Request select(long id,int... positions) { var ids=itemIds(id); return new Request(Arrays.stream(positions).mapToObj(ids::get).toList()); }
 BigDecimal refunds(long id) { return jdbc.queryForObject("SELECT COALESCE(SUM(r.amount),0) FROM refunds r JOIN payments p ON p.id=r.payment_id WHERE p.booking_id=?",BigDecimal.class,id); }
 @Test void paidQuoteIsReadOnlyAndSequentialCancellationPreservesIdentityTicketsAndHistory() {
  var f=expanded(); var owner=customer("m21-paid"); authenticate(owner.user()); var b=web(f,owner,0,1,2); paymentService.confirm(owner.user(),b.bookingId());
  var original=jdbc.queryForList("SELECT id FROM tickets WHERE booking_id=? ORDER BY booking_item_id",Long.class,b.bookingId());
  var quote=partial.quote(owner.user(),b.bookingId(),false,select(b.bookingId(),1));
  assertThat(quote.currentTotal()).isEqualByComparingTo("600"); assertThat(quote.refundRequired()).isEqualByComparingTo("200");
  assertThat(refunds(b.bookingId())).isZero(); assertThat(partial.history(owner.user(),b.bookingId(),false)).isEmpty();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking_items WHERE booking_id=? AND cancelled=TRUE",Integer.class,b.bookingId())).isZero();
  var done=partial.execute(owner.user(),b.bookingId(),false,select(b.bookingId(),1));
  assertThat(done.quote().newTotal()).isEqualByComparingTo("400"); assertThat(done.quote().bookingCode()).isEqualTo(b.bookingCode());
  assertThat(paymentService.ticket(owner.user(),b.bookingId()).tickets().stream().map(t->t.ticketId())).containsExactly(original.get(0),original.get(2));
  paymentService.confirm(owner.user(),b.bookingId());
  assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE id=?",String.class,original.get(1))).isEqualTo("VOID");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory WHERE booking_item_id=?",Integer.class,itemIds(b.bookingId()).get(1))).isZero();
  partial.execute(owner.user(),b.bookingId(),false,select(b.bookingId(),0));
  assertThat(partial.history(owner.user(),b.bookingId(),false)).hasSize(2);
  assertThatThrownBy(()->partial.execute(owner.user(),b.bookingId(),false,select(b.bookingId(),2))).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getCode()).isEqualTo("FULL_CANCELLATION_REQUIRED"));
  cancellations.customerCancel(owner.user(),b.bookingId(),new CancellationDtos.Request(null));
  assertThat(refunds(b.bookingId())).isEqualByComparingTo("600"); assertThat(partial.history(owner.user(),b.bookingId(),false)).hasSize(2);
 }
 @Test void partialThenSeatAndTripChangesMoveOnlyActiveItemsIncludingReuseOfCancelledSeat() {
  var f=expanded(); var owner=customer("m21-mod"); authenticate(owner.user()); var b=web(f,owner,0,1,2); paymentService.confirm(owner.user(),b.bookingId());
  var ids=itemIds(b.bookingId()); partial.execute(owner.user(),b.bookingId(),false,new Request(List.of(ids.get(1))));
  var seat=modifications.create(owner.user(),b.bookingId(),false,new ModificationDtos.Request(ModificationDtos.Type.SEAT_CHANGE,f.trip().getId(),List.of(new ModificationDtos.Selection(ids.get(0),f.seats().get(1).getId()))));
  modifications.confirm(owner.user(),b.bookingId(),false,seat.id());
  var target=targetTrip(f); var seats=targetSeats(target);
  var trip=modifications.create(owner.user(),b.bookingId(),false,new ModificationDtos.Request(ModificationDtos.Type.TRIP_CHANGE,target.getId(),List.of(new ModificationDtos.Selection(ids.get(0),seats.get(0)),new ModificationDtos.Selection(ids.get(2),seats.get(1)))));
  modifications.confirm(owner.user(),b.bookingId(),false,trip.id());
  assertThat(jdbc.queryForObject("SELECT cancelled FROM booking_items WHERE id=?",Boolean.class,ids.get(1))).isTrue();
  assertThat(jdbc.queryForObject("SELECT trip_seat_id FROM booking_items WHERE id=?",Long.class,ids.get(1))).isEqualTo(f.seats().get(1).getId());
  assertThat(modifications.eligibility(owner.user(),b.bookingId(),false).items()).hasSize(2);
  assertThat(paymentService.ticket(owner.user(),b.bookingId()).tickets()).hasSize(2);
  cancellations.customerCancel(owner.user(),b.bookingId(),new CancellationDtos.Request(null)); assertThat(refunds(b.bookingId())).isEqualByComparingTo("600");
 }
 @ParameterizedTest @ValueSource(strings={"PAY_ON_BOARD","QR_TRANSFER"})
 void unpaidPhoneCreatesNoCashOrTicketsAndRevokesOldLink(String method) {
  var f=expanded(); var admin=actor(f,RoleCode.OPERATOR_ADMIN); authenticate(admin); authenticate(admin);
  var b=assisted.create(admin,new AssistedBookingDtos.CreateRequest(f.trip().getId(),f.locations().get(0).getId(),f.locations().get(2).getId(),f.seats().subList(0,3).stream().map(s->s.getId()).toList(),"Caller","0901234567",null,PaymentMethod.valueOf(method)));
  String old=method.equals("QR_TRANSFER")?token(assisted.issueLink(admin,b.bookingId())):null;
  Object deadline=jdbc.queryForObject("SELECT payment_due_at FROM bookings WHERE id=?",Object.class,b.bookingId());
  var result=partial.execute(admin,b.bookingId(),true,select(b.bookingId(),1));
  assertThat(result.quote().newAmountDue()).isEqualByComparingTo("400"); assertThat(result.quote().refundRequired()).isZero();
  assertThat(count("payments",b.bookingId())).isZero(); assertThat(count("tickets",b.bookingId())).isZero();
  assertThat(jdbc.queryForObject("SELECT payment_due_at FROM bookings WHERE id=?",Object.class,b.bookingId())).isEqualTo(deadline);
  if(old!=null) {
   assertThatThrownBy(()->assisted.publicContext(old)).isInstanceOf(com.busgo.common.exception.ResourceNotFoundException.class);
   assertThat(assisted.publicContext(token(assisted.issueLink(admin,b.bookingId()))).seats()).hasSize(2);
  }
  assisted.record(admin,b.bookingId(),new AssistedBookingDtos.RecordPayment(PaymentMethod.valueOf(method),null));
  assertThat(count("tickets",b.bookingId())).isEqualTo(2);
 }
 @Test void existingAdjustmentAndRefundHistoryIsAccountedAcrossPayments() {
  var f=expanded(); var owner=customer("m21-money"); authenticate(owner.user()); var b=web(f,owner,0,1,2); paymentService.confirm(owner.user(),b.bookingId());
  for(String fare:List.of("300","250")) {
   var t=targetTrip(f); jdbc.update("UPDATE operator_route_fares SET price=? WHERE operator_route_id=?",new BigDecimal(fare),f.operatorRoute().getId()); em.clear();
   var m=modifications.create(owner.user(),b.bookingId(),false,tripRequest(b.bookingId(),t)); modifications.confirm(owner.user(),b.bookingId(),false,m.id());
  }
  var snapshots=jdbc.queryForList("SELECT id,amount,paid_at,purpose FROM payments WHERE booking_id=? ORDER BY id",b.bookingId());
  var result=partial.execute(owner.user(),b.bookingId(),false,select(b.bookingId(),0,1));
  assertThat(result.quote().netCollected()).isEqualByComparingTo("750"); assertThat(result.quote().refundRequired()).isEqualByComparingTo("500");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refunds WHERE partial_cancellation_id=?",Integer.class,result.id())).isEqualTo(2);
  assertThat(jdbc.queryForList("SELECT id,amount,paid_at,purpose FROM payments WHERE booking_id=? ORDER BY id",b.bookingId())).isEqualTo(snapshots);
  cancellations.customerCancel(owner.user(),b.bookingId(),new CancellationDtos.Request(null)); assertThat(refunds(b.bookingId())).isEqualByComparingTo("900");
 }
 @ParameterizedTest @ValueSource(strings={"CHECKED_IN","BOARDED","NO_SHOW"})
 void onlySelectedTerminalAttendanceBlocks(String status) {
  var f=expanded(); var owner=customer("m21-att"); authenticate(owner.user()); var b=web(f,owner,0,1,2); paymentService.confirm(owner.user(),b.bookingId());
  jdbc.update("UPDATE ticket_boarding SET status=? WHERE booking_item_id=?",status,itemIds(b.bookingId()).get(0));
  assertThatThrownBy(()->partial.quote(owner.user(),b.bookingId(),false,select(b.bookingId(),0))).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getCode()).isEqualTo("ATTENDANCE_CONFLICT"));
  partial.execute(owner.user(),b.bookingId(),false,select(b.bookingId(),1));
  var admin=actor(f,RoleCode.OPERATOR_ADMIN); authenticate(admin);
  assertThat(operations.attendance(admin,f.trip().getId())).hasSize(2);
  assertThat(jdbc.queryForObject("SELECT status FROM ticket_boarding WHERE booking_item_id=?",String.class,itemIds(b.bookingId()).get(0))).isEqualTo(status);
 }
 @Test void customerCutoffOperatorExemptionAndActiveModificationGuard() {
  var f=expanded(); var owner=customer("m21-cut"); var b=web(f,owner,0,1,2);
  var m=modifications.create(owner.user(),b.bookingId(),false,seatRequest(b.bookingId(),f,3));
  assertThat(partial.eligibility(owner.user(),b.bookingId(),false).eligibility().reasonCode()).isEqualTo("MODIFICATION_ACTIVE");
  assertThatThrownBy(()->partial.execute(owner.user(),b.bookingId(),false,select(b.bookingId(),0))).isInstanceOf(BusinessException.class);
  modifications.cancel(owner.user(),b.bookingId(),false,m.id());
  jdbc.update("UPDATE trip_stops SET planned_departure_time=? WHERE id=?",com.busgo.common.time.JpaJdbcTime.parameter(LocalDateTime.now(ZoneOffset.UTC).plusHours(5)),f.stops().get(0).getId()); em.clear();
  assertThat(partial.eligibility(owner.user(),b.bookingId(),false).eligibility().reasonCode()).isEqualTo("CUSTOMER_CUTOFF");
  var admin=actor(f,RoleCode.OPERATOR_ADMIN); authenticate(admin); partial.execute(admin,b.bookingId(),true,select(b.bookingId(),0));
 }
 @ParameterizedTest @ValueSource(strings={"BOARDING","DEPARTED","COMPLETED","CANCELLED"})
 void tripLifecycleBlocksBothActors(String status) {
  var f=expanded(); var owner=customer("m21-trip"); var b=web(f,owner,0,1);
  jdbc.update("UPDATE trips SET status=? WHERE id=?",status,f.trip().getId()); em.clear();
  assertThat(partial.eligibility(owner.user(),b.bookingId(),false).eligibility().allowed()).isFalse();
  var admin=actor(f,RoleCode.OPERATOR_ADMIN); authenticate(admin);
  assertThat(partial.eligibility(admin,b.bookingId(),true).eligibility().allowed()).isFalse();
 }
 @Test void selectionAndSecurityAreAuthoritative() throws Exception {
  var f=expanded(); var owner=customer("m21-sec"); var other=customer("m21-other"); var b=web(f,owner,0,1,2); var req=select(b.bookingId(),0);
  String path="/api/v1/bookings/"+b.bookingId(); String op="/api/v1/operator/bookings/"+b.bookingId();
  mvc.perform(get(path+"/partial-cancellation-eligibility").header("Authorization","Bearer "+other.token())).andExpect(status().isNotFound());
  var staff=actor(f,RoleCode.OPERATOR_STAFF); var system=actor(f,RoleCode.SYSTEM_ADMIN); var foreign=actor(fixture(),RoleCode.OPERATOR_ADMIN);
  for(var actor:List.of(staff,system)) mvc.perform(post(op+"/partial-cancellations").header("Authorization",bearer(actor)).contentType("application/json").content("{\"bookingItemIds\":["+req.bookingItemIds().get(0)+"]}")).andExpect(status().isForbidden());
  mvc.perform(get(op+"/partial-cancellation-eligibility").header("Authorization",bearer(staff))).andExpect(status().isOk()).andExpect(jsonPath("data.eligibility.reasonCode").value("ADMIN_REQUIRED"));
  mvc.perform(get(op+"/partial-cancellations").header("Authorization",bearer(foreign))).andExpect(status().isNotFound());
  assertThatThrownBy(()->partial.quote(owner.user(),b.bookingId(),false,new Request(List.of(req.bookingItemIds().get(0),req.bookingItemIds().get(0))))).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->partial.quote(owner.user(),b.bookingId(),false,select(b.bookingId(),0,1,2))).isInstanceOf(BusinessException.class);
  partial.execute(owner.user(),b.bookingId(),false,req);
  assertThatThrownBy(()->partial.execute(owner.user(),b.bookingId(),false,req)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getCode()).isEqualTo("ITEM_CANCELLED"));
 }
 @Test void foreignItemAndHistoryAreConcealedAndInactiveOperatorIsAuthoritative() {
  var f=expanded(); var owner=customer("m21-owner-history"); var a=web(f,owner,0,1); var b=web(f,owner,2,3);
  assertThatThrownBy(()->partial.quote(owner.user(),a.bookingId(),false,select(b.bookingId(),0))).isInstanceOf(com.busgo.common.exception.ResourceNotFoundException.class);
  var result=partial.execute(owner.user(),a.bookingId(),false,select(a.bookingId(),0));
  assertThatThrownBy(()->partial.read(owner.user(),b.bookingId(),false,result.id())).isInstanceOf(com.busgo.common.exception.ResourceNotFoundException.class);
  assertThat(partial.read(owner.user(),a.bookingId(),false,result.id()).quote().items().get(0).cancelled()).isTrue();
  var admin=actor(f,RoleCode.OPERATOR_ADMIN); authenticate(admin);
  jdbc.update("UPDATE transport_operators SET status='INACTIVE' WHERE id=?",f.operator().getId()); em.clear();
  assertThat(partial.eligibility(owner.user(),b.bookingId(),false).eligibility().reasonCode()).isEqualTo("OPERATOR_INACTIVE");
  assertThatThrownBy(()->partial.execute(admin,b.bookingId(),true,select(b.bookingId(),0))).isInstanceOf(BusinessException.class);
 }

}

