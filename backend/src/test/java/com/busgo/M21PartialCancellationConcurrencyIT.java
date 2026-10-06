package com.busgo;
import static org.assertj.core.api.Assertions.*;
import com.busgo.booking.*;
import com.busgo.booking.PartialCancellationDtos.Request;
import com.busgo.common.security.CurrentUser;
import com.busgo.payment.entity.PaymentMethod;
import com.busgo.user.entity.RoleCode;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
@SpringBootTest @ActiveProfiles("dev")
class M21PartialCancellationConcurrencyIT extends M20Support {
 @Autowired TransactionTemplate tx;
 @Autowired PartialCancellationService partial;
 @Autowired CancellationService cancellations;
 private Fixture f; private CurrentUser admin;
 @BeforeEach void setup() { f=tx.execute(s->expanded()); admin=tx.execute(s->actor(f,RoleCode.OPERATOR_ADMIN)); authenticate(admin); }
 private long booking(boolean paid) {
  long id=assisted.create(admin,new AssistedBookingDtos.CreateRequest(f.trip().getId(),f.locations().get(0).getId(),f.locations().get(2).getId(),f.seats().subList(0,3).stream().map(s->s.getId()).toList(),"Race","0901234567",null,PaymentMethod.PAY_ON_BOARD)).bookingId();
  if(paid) assisted.record(admin,id,new AssistedBookingDtos.RecordPayment(PaymentMethod.PAY_ON_BOARD,null)); return id;
 }
 private Request selection(long id) { return new Request(List.of(itemIds(id).get(0))); }
 private BigDecimal refunded(long id) { return jdbc.queryForObject("SELECT COALESCE(SUM(r.amount),0) FROM refunds r JOIN payments p ON p.id=r.payment_id WHERE p.booking_id=?",BigDecimal.class,id); }
    private List<String> race(Callable<?> a,Callable<?> b) throws Exception {
        var pool=Executors.newFixedThreadPool(2); var gate=new CountDownLatch(1);
        try {
            var futures=new ArrayList<Future<String>>();
            for(var action:List.of(a,b)) futures.add(pool.submit(()->{
                authenticate(admin); gate.await();
                try { action.call(); return "OK"; }
                catch(com.busgo.common.exception.BusinessException e) { return e.getCode(); }

                finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
            }));
            gate.countDown(); return List.of(futures.get(0).get(20,TimeUnit.SECONDS),futures.get(1).get(20,TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }

 @Test void sameItemHasOneWinnerAndExactlyOneRefundAndRelease() throws Exception {
  long id=booking(true); var req=selection(id);
  var before=jdbc.queryForList("SELECT version FROM trip_seat_segment_inventory WHERE booking_item_id=? ORDER BY id",Long.class,req.bookingItemIds().get(0));
  assertThat(race(()->partial.execute(admin,id,true,req),()->partial.execute(admin,id,true,req))).containsExactlyInAnyOrder("OK","ITEM_CANCELLED");
  assertThat(refunded(id)).isEqualByComparingTo("200");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM partial_cancellations WHERE booking_id=?",Integer.class,id)).isOne();
  var after=jdbc.queryForList("SELECT version FROM trip_seat_segment_inventory WHERE trip_seat_id=? ORDER BY id",Long.class,f.seats().get(0).getId());
  for(int i=0;i<before.size();i++) assertThat(after.get(i)).isEqualTo(before.get(i)+1);
 }
 @Test void fullCancellationSerializesAndNeverRefundsOrReleasesTwice() throws Exception {
  long id=booking(true); var req=selection(id);
  var result=race(()->partial.execute(admin,id,true,req),()->cancellations.operatorCancel(admin,id,new CancellationDtos.Request(null)));
  assertThat(result).allMatch(r->Set.of("OK","BOOKING_NOT_CANCELLABLE").contains(r));
  assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,id)).isEqualTo("CANCELLED");
  assertThat(refunded(id)).isEqualByComparingTo("600");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory v JOIN booking_items i ON i.id=v.booking_item_id WHERE i.booking_id=?",Integer.class,id)).isZero();
 }
 @Test void modificationCreationSerializesWithPartialCancellationWithoutReactivation() throws Exception {
  long id=booking(true); var req=selection(id); var change=seatRequest(id,f,3);
  var result=race(()->partial.execute(admin,id,true,req),()->modifications.create(admin,id,true,change));
  // If partial wins, M20 conceals the now-inactive item as 404; if M20 wins, M21 rejects its active attempt.
  assertThat(result).contains("OK");
  assertThat(result).anyMatch(r->Set.of("MODIFICATION_ACTIVE","BOOKING_NOT_FOUND").contains(r));
  var histories=modifications.history(admin,id,true);
  if(!histories.isEmpty()) { modifications.confirm(admin,id,true,histories.get(0).id()); assertThat(refunded(id)).isZero(); }
  else assertThat(jdbc.queryForObject("SELECT cancelled FROM booking_items WHERE id=?",Boolean.class,req.bookingItemIds().get(0))).isTrue();
 }
 @Test void paymentCompletionUsesCurrentLockedAmountAndNeverOverRefunds() throws Exception {
  long id=booking(false); var req=selection(id);
  assertThat(race(()->partial.execute(admin,id,true,req),()->assisted.record(admin,id,new AssistedBookingDtos.RecordPayment(PaymentMethod.PAY_ON_BOARD,null)))).containsOnly("OK");
  BigDecimal collected=jdbc.queryForObject("SELECT SUM(amount) FROM payments WHERE booking_id=?",BigDecimal.class,id);
  assertThat(collected.subtract(refunded(id))).isEqualByComparingTo("400");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets t JOIN booking_items i ON i.id=t.booking_item_id WHERE i.booking_id=? AND i.cancelled=FALSE AND t.status='VALID'",Integer.class,id)).isEqualTo(2);
 }
 @Test void injectedFinalWriteFailureRollsBackEveryArtifactAndOriginalLink() {
  long id=booking(true); var req=selection(id);
  var before=jdbc.queryForList("SELECT * FROM trip_seat_segment_inventory WHERE trip_seat_id IN (?,?,?) ORDER BY id",f.seats().get(0).getId(),f.seats().get(1).getId(),f.seats().get(2).getId());
  jdbc.execute("CREATE TRIGGER m21_fail_total BEFORE UPDATE ON bookings FOR EACH ROW BEGIN IF NEW.id="+id+" AND NEW.total_amount<OLD.total_amount THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='M21 injected rollback'; END IF; END");
  try { assertThatThrownBy(()->partial.execute(admin,id,true,req)).isInstanceOf(RuntimeException.class); }
  finally { jdbc.execute("DROP TRIGGER m21_fail_total"); }
  assertThat(jdbc.queryForObject("SELECT total_amount FROM bookings WHERE id=?",BigDecimal.class,id)).isEqualByComparingTo("600");
  assertThat(refunded(id)).isZero(); assertThat(partial.history(admin,id,true)).isEmpty();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking_items WHERE booking_id=? AND cancelled=TRUE",Integer.class,id)).isZero();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE booking_id=? AND status='VALID'",Integer.class,id)).isEqualTo(3);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ticket_boarding a JOIN booking_items i ON i.id=a.booking_item_id WHERE i.booking_id=? AND a.status='EXPECTED'",Integer.class,id)).isEqualTo(3);
  assertThat(jdbc.queryForList("SELECT * FROM trip_seat_segment_inventory WHERE trip_seat_id IN (?,?,?) ORDER BY id",f.seats().get(0).getId(),f.seats().get(1).getId(),f.seats().get(2).getId())).isEqualTo(before);
 }

 @Test void failedPendingQrCancellationRetainsOldLinkAndDeadline() {
  var b=assisted.create(admin,new AssistedBookingDtos.CreateRequest(f.trip().getId(),f.locations().get(0).getId(),f.locations().get(2).getId(),f.seats().subList(0,3).stream().map(x->x.getId()).toList(),"QR rollback","0901234567",null,PaymentMethod.QR_TRANSFER));
  String link=token(assisted.issueLink(admin,b.bookingId())); var before=jdbc.queryForMap("SELECT payment_token_hash,payment_due_at,total_amount FROM bookings WHERE id=?",b.bookingId());
  jdbc.execute("CREATE TRIGGER m21_fail_qr BEFORE UPDATE ON bookings FOR EACH ROW BEGIN IF NEW.id="+b.bookingId()+" AND NEW.total_amount<OLD.total_amount THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='M21 QR rollback'; END IF; END");
  try { assertThatThrownBy(()->partial.execute(admin,b.bookingId(),true,selection(b.bookingId()))).isInstanceOf(RuntimeException.class); }
  finally { jdbc.execute("DROP TRIGGER m21_fail_qr"); }
  assertThat(jdbc.queryForMap("SELECT payment_token_hash,payment_due_at,total_amount FROM bookings WHERE id=?",b.bookingId())).isEqualTo(before);
  assertThat(assisted.publicContext(link).amount()).isEqualByComparingTo("600"); assertThat(partial.history(admin,b.bookingId(),true)).isEmpty();
 }
    @AfterEach void cleanup() {
        long op=f.operator().getId();
        tx.executeWithoutResult(s->{
            String bookings="SELECT b.id FROM bookings b JOIN trips t ON t.id=b.trip_id JOIN operator_routes o ON o.id=t.operator_route_id WHERE o.operator_id="+op;
            jdbc.update("DELETE FROM partial_cancellation_items WHERE cancellation_id IN (SELECT id FROM partial_cancellations WHERE operator_id=?)",op);
            jdbc.update("DELETE FROM booking_modification_items WHERE modification_id IN (SELECT id FROM booking_modifications WHERE operator_id=?)",op);
            jdbc.update("DELETE a FROM ticket_boarding a JOIN booking_items i ON i.id=a.booking_item_id WHERE i.booking_id IN ("+bookings+")");
            jdbc.update("DELETE FROM tickets WHERE booking_id IN ("+bookings+")");
            jdbc.update("DELETE r FROM refunds r JOIN payments p ON p.id=r.payment_id WHERE p.booking_id IN ("+bookings+")");
            jdbc.update("DELETE FROM partial_cancellations WHERE operator_id=?",op);
            jdbc.update("DELETE FROM payments WHERE booking_id IN ("+bookings+")");
            jdbc.update("DELETE FROM booking_modifications WHERE operator_id=?",op);
            jdbc.update("DELETE FROM booking_status_history WHERE booking_id IN ("+bookings+")");
            jdbc.update("UPDATE trip_seat_segment_inventory v JOIN booking_items i ON i.id=v.booking_item_id SET v.booking_item_id=NULL,v.status='AVAILABLE' WHERE i.booking_id IN ("+bookings+")");
            jdbc.update("DELETE FROM booking_items WHERE booking_id IN ("+bookings+")");
            jdbc.update("DELETE FROM bookings WHERE id IN (SELECT id FROM ("+bookings+") x)");
            String tripIds="SELECT t.id FROM trips t JOIN operator_routes o ON o.id=t.operator_route_id WHERE o.operator_id="+op;
            jdbc.update("DELETE FROM trip_seat_segment_inventory WHERE trip_seat_id IN (SELECT id FROM trip_seats WHERE trip_id IN ("+tripIds+"))");
            jdbc.update("DELETE FROM trip_seats WHERE trip_id IN ("+tripIds+")");
            jdbc.update("DELETE FROM trip_segments WHERE trip_id IN ("+tripIds+")");
            jdbc.update("DELETE FROM trip_stops WHERE trip_id IN ("+tripIds+")");
            jdbc.update("DELETE FROM trips WHERE operator_route_id=?",f.operatorRoute().getId());
            jdbc.update("DELETE FROM operator_route_fares WHERE operator_route_id=?",f.operatorRoute().getId());
            jdbc.update("DELETE FROM buses WHERE id=?",f.bus().getId());
            jdbc.update("DELETE FROM seat_templates WHERE bus_type_id=?",f.busType().getId());
            jdbc.update("DELETE FROM bus_types WHERE id=?",f.busType().getId());
            jdbc.update("DELETE FROM operator_routes WHERE id=?",f.operatorRoute().getId());
            jdbc.update("DELETE FROM route_stops WHERE route_id=?",f.route().getId());
            jdbc.update("DELETE FROM routes WHERE id=?",f.route().getId());
            jdbc.update("DELETE FROM operator_staff WHERE operator_id=?",op);
            jdbc.update("DELETE FROM transport_operators WHERE id=?",op);
            for(var location:f.locations()) jdbc.update("DELETE FROM locations WHERE id=?",location.getId());
            jdbc.update("DELETE FROM user_roles WHERE user_id=?",admin.id());
            jdbc.update("DELETE FROM users WHERE id=?",admin.id());
        });
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}

