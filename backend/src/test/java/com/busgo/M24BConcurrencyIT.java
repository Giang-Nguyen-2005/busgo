package com.busgo;

import static org.assertj.core.api.Assertions.*;
import com.busgo.booking.*;
import com.busgo.common.exception.BusinessException;
import com.busgo.common.security.CurrentUser;
import com.busgo.promotion.*;
import com.busgo.promotion.VoucherDtos.Terms;
import com.busgo.user.entity.RoleCode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties="busgo.notifications.initial-delay=PT24H") @ActiveProfiles("dev")
class M24BConcurrencyIT extends M20Support {
    @Autowired VoucherService vouchers;
    @Autowired CancellationService cancellations;
    @Autowired TransactionTemplate tx;
    Fixture f; CurrentUser admin; UserAuth a,b; long voucher;
    @BeforeEach void setup() {
        tx.executeWithoutResult(s->{f=expanded();admin=actor(f,RoleCode.OPERATOR_ADMIN);a=customer("m24bracea");b=customer("m24braceb");authenticate(admin);
            voucher=((Number)vouchers.create(admin,new Terms("LAST","Last use",null,"FIXED_AMOUNT",new BigDecimal("10"),null,BigDecimal.ZERO,OffsetDateTime.now().minusDays(1),OffsetDateTime.now().plusDays(1),1,1,null)).get("id")).longValue();vouchers.state(admin,voucher,true);});
    }
    BookingDtos.BookingResponse create(UserAuth u,String token) {authenticate(u.user());return bookingService.create(u.user(),new BookingDtos.CreateBookingRequest(token,"C","0901234567",null,"LAST"));}
    List<String> race(Callable<?> first,Callable<?> second) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {var futures=new ArrayList<Future<String>>();for(var call:List.of(first,second)) futures.add(pool.submit(()->{gate.await();try {call.call();return "OK";}catch(BusinessException e){return e.getCode();}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}}));gate.countDown();return List.of(futures.get(0).get(20,TimeUnit.SECONDS),futures.get(1).get(20,TimeUnit.SECONDS));}finally{pool.shutdownNow();}
    }
    @Test void finalUseAcrossDifferentTripsSerializesAtOperator() throws Exception {
        var target=tx.execute(s->targetTrip(f));var ah=hold(a,f,0,2,0);
        var seat=jdbc.queryForObject("SELECT MIN(id) FROM trip_seats WHERE trip_id=?",Long.class,target.getId());
        var bh=holds.create(b.user(),new com.busgo.hold.SeatHoldDtos.CreateSeatHoldRequest(target.getId(),f.locations().get(0).getId(),f.locations().get(2).getId(),List.of(seat)));
        assertThat(race(()->create(a,ah.holdToken()),()->create(b,bh.holdToken()))).containsExactlyInAnyOrder("OK","VOUCHER_LIMIT_REACHED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_usages WHERE voucher_id=?",Long.class,voucher)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT hold_token) FROM trip_seat_segment_inventory WHERE hold_token IN (?,?)",Long.class,ah.holdToken(),bh.holdToken())).isOne();
    }
    @Test void duplicateSubmissionCannotReserveTwice() throws Exception {
        var h=hold(a,f,0,2,0);var results=race(()->create(a,h.holdToken()),()->create(a,h.holdToken()));assertThat(results.stream().filter("OK"::equals).count()).isOne();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_usages WHERE voucher_id=?",Long.class,voucher)).isOne();
    }
    @Test void simultaneousPauseAndBookingHaveOnlyValidOutcomes() throws Exception {
        var h=hold(a,f,0,2,0);var results=race(()->create(a,h.holdToken()),()->{authenticate(admin);return vouchers.state(admin,voucher,false);});
        assertThat(results).allMatch(x->Set.of("OK","VOUCHER_INACTIVE").contains(x));
        var ids=jdbc.queryForList("SELECT booking_id FROM voucher_usages WHERE voucher_id=?",Long.class,voucher);
        if(!ids.isEmpty()) {authenticate(a.user());paymentService.confirm(a.user(),ids.get(0));assertThat(jdbc.queryForObject("SELECT state FROM voucher_usages WHERE voucher_id=?",String.class,voucher)).isEqualTo("REDEEMED");}
    }
    @Test void paymentAndCancellationNeverDoubleRedeemOrRelease() throws Exception {
        var booking=create(a,hold(a,f,0,2,0).holdToken());
        assertThat(race(()->{authenticate(a.user());return paymentService.confirm(a.user(),booking.bookingId());},()->{authenticate(a.user());return cancellations.customerCancel(a.user(),booking.bookingId(),new CancellationDtos.Request(null));})).allMatch(x->Set.of("OK","BOOKING_NOT_PAYABLE").contains(x));
        String state=jdbc.queryForObject("SELECT state FROM voucher_usages WHERE voucher_id=?",String.class,voucher);assertThat(state).isIn("REDEEMED","RELEASED");assertThat(count("voucher_usages",booking.bookingId())).isOne();
    }
    @Test void failureAfterReservationRollsBackBookingAndHoldConversion() {
        var h=hold(a,f,0,2,0);
        assertThatThrownBy(()->tx.executeWithoutResult(s->{create(a,h.holdToken());throw new IllegalStateException("Injected transaction failure");})).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_usages WHERE voucher_id=?",Long.class,voucher)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE trip_id=?",Long.class,f.trip().getId())).isZero();
        create(a,h.holdToken());
    }
    @Test void expiredPaymentAndExpiryReleaseOnlyOnce() throws Exception {
        var booking=create(a,hold(a,f,0,2,0).holdToken());
        tx.executeWithoutResult(s->{var entity=em.find(com.busgo.booking.entity.Booking.class,booking.bookingId());entity.setPaymentDueAt(com.busgo.common.time.BusGoTime.utc(java.time.Instant.now().minusSeconds(5)));em.flush();});
        var results=race(()->{authenticate(a.user());return paymentService.confirm(a.user(),booking.bookingId());},()->cancellations.expire(booking.bookingId()));
        assertThat(results.stream().filter("OK"::equals).count()).isOne();
        assertThat(jdbc.queryForObject("SELECT state FROM voucher_usages WHERE voucher_id=?",String.class,voucher)).isEqualTo("RELEASED");assertThat(count("payments",booking.bookingId())).isZero();
    }
    @Test void suspensionAndVoucherPauseDoNotInvertLocks() throws Exception {
        var h=hold(a,f,0,2,0);
        assertThat(race(()->tx.execute(s->{jdbc.queryForObject("SELECT id FROM transport_operators WHERE id=? FOR UPDATE",Long.class,f.operator().getId());jdbc.update("UPDATE transport_operators SET status='SUSPENDED' WHERE id=?",f.operator().getId());return null;}),()->{authenticate(admin);return vouchers.state(admin,voucher,false);})).containsOnly("OK");
        assertThatThrownBy(()->create(a,h.holdToken())).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_usages WHERE voucher_id=?",Long.class,voucher)).isZero();
    }
    @Test void renamedCodeCannotBeAcceptedFromEarlierRepeatableReadSnapshot() throws Exception {
        var h=hold(a,f,0,2,0);var locked=new CountDownLatch(1);var snapshot=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var rename=pool.submit(()->tx.execute(s->{
                jdbc.queryForObject("SELECT id FROM transport_operators WHERE id=? FOR UPDATE",Long.class,f.operator().getId());locked.countDown();
                try {if(!snapshot.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Missing checkout snapshot");}catch(InterruptedException e){throw new RuntimeException(e);}
                authenticate(admin);return vouchers.edit(admin,voucher,new Terms("RENAMED","Renamed",null,"FIXED_AMOUNT",BigDecimal.TEN,null,BigDecimal.ZERO,OffsetDateTime.now().minusDays(1),OffsetDateTime.now().plusDays(1),1,1,null));
            }));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            var checkout=pool.submit(()->{try {tx.executeWithoutResult(s->{jdbc.queryForObject("SELECT COUNT(*) FROM operator_vouchers WHERE id=?",Long.class,voucher);snapshot.countDown();create(a,h.holdToken());});return "OK";}catch(BusinessException e){return e.getCode();}});
            rename.get(10,TimeUnit.SECONDS);assertThat(checkout.get(10,TimeUnit.SECONDS)).isEqualTo("VOUCHER_NOT_ELIGIBLE");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_usages WHERE voucher_id=?",Long.class,voucher)).isZero();
        } finally {snapshot.countDown();pool.shutdownNow();}
    }
    @AfterEach void cleanup() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        tx.executeWithoutResult(s->{jdbc.update("DELETE FROM voucher_usages WHERE voucher_id=?",voucher);jdbc.update("DELETE FROM voucher_management_history WHERE voucher_id=?",voucher);jdbc.update("UPDATE bookings SET voucher_id=NULL WHERE voucher_id=?",voucher);jdbc.update("DELETE FROM operator_vouchers WHERE id=?",voucher);NotificationTestCleanup.fixture(jdbc,f);for(long id:List.of(a.user().id(),b.user().id(),admin.id())) {jdbc.update("DELETE FROM user_roles WHERE user_id=?",id);jdbc.update("DELETE FROM users WHERE id=?",id);}});
    }
}
