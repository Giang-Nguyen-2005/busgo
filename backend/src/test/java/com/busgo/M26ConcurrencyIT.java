package com.busgo;

import static org.assertj.core.api.Assertions.*;
import com.busgo.onboarding.OnboardingDtos.*;
import com.busgo.common.security.CurrentUser;
import com.busgo.common.exception.BusinessException;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties="busgo.notifications.initial-delay=PT24H") @ActiveProfiles("dev")
class M26ConcurrencyIT extends M26Support {
    @Autowired TransactionTemplate tx;
    CurrentUser admin;
    final List<Long> applications=new ArrayList<>();
    @BeforeEach void setup() { admin=tx.execute(s->system()); }
    long application() { long id=submit(request());applications.add(id);return id; }
    List<String> race(Callable<?> a,Callable<?> b) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            var results=new ArrayList<Future<String>>();for(var action:List.of(a,b)) results.add(pool.submit(()->{gate.await();try{action.call();return "OK";}catch(BusinessException e){return e.getCode();}}));
            gate.countDown();return List.of(results.get(0).get(20,TimeUnit.SECONDS),results.get(1).get(20,TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
    @Test void concurrentApprovalOneOperatorOneCurrentInvite() throws Exception {
        long id=application();onboarding.review(admin,id,new Decision(null));
        assertThat(race(()->onboarding.approve(admin,id,new Decision(null)),()->onboarding.approve(admin,id,new Decision(null)))).containsExactlyInAnyOrder("OK","INVALID_ONBOARDING_STATE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transport_operators WHERE code=?",Long.class,"ONB-"+id)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operator_admin_invitations WHERE application_id=? AND revoked_at IS NULL",Long.class,id)).isOne();
    }
    @Test void concurrentAcceptanceConsumesOnce() throws Exception {
        var i=approve(admin,application());assertThat(race(()->onboarding.accept(raw(i),acceptance()),()->onboarding.accept(raw(i),acceptance()))).containsExactlyInAnyOrder("OK","INVALID_INVITATION");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operator_staff WHERE operator_id=?",Long.class,i.operatorId())).isOne();
    }
    @Test void reissueWinnerInvalidatesWaitingOldAcceptance() throws Exception {
        var i=approve(admin,application());var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var replace=pool.submit(()->tx.execute(s->{jdbc.queryForObject("SELECT id FROM operator_applications WHERE id=? FOR UPDATE",Long.class,i.applicationId());locked.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){throw new RuntimeException(e);}return onboarding.reissue(admin,i.applicationId());}));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();var accept=pool.submit(()->{try{onboarding.accept(raw(i),acceptance());return "OK";}catch(BusinessException e){return e.getCode();}});release.countDown();
            var newer=replace.get(10,TimeUnit.SECONDS);assertThat(accept.get(10,TimeUnit.SECONDS)).isEqualTo("INVALID_INVITATION");onboarding.accept(raw(newer),acceptance());
        } finally {release.countDown();pool.shutdownNow();}
    }
    @Test void concurrentSuspensionsAppendOneTransition() throws Exception {
        var i=approve(admin,application());onboarding.accept(raw(i),acceptance());
        assertThat(race(()->onboarding.status(admin,i.operatorId(),new Reason("Review"),true),()->onboarding.status(admin,i.operatorId(),new Reason("Review"),true))).containsExactlyInAnyOrder("OK","INVALID_ONBOARDING_STATE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operator_onboarding_history WHERE operator_id=? AND event_type='OPERATOR_SUSPENDED'",Long.class,i.operatorId())).isOne();
        assertThat(race(()->onboarding.status(admin,i.operatorId(),new Reason("Resolved"),false),()->onboarding.status(admin,i.operatorId(),new Reason("Resolved"),false))).containsExactlyInAnyOrder("OK","INVALID_ONBOARDING_STATE");
    }
    @Test void approvalFailureRollsBackEveryWrite() {
        long id=application();onboarding.review(admin,id,new Decision(null));
        jdbc.execute("CREATE TRIGGER m26_fail_invite BEFORE INSERT ON operator_admin_invitations FOR EACH ROW BEGIN IF NEW.application_id="+id+" THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='M26 injected failure'; END IF; END");
        try {assertThatThrownBy(()->onboarding.approve(admin,id,new Decision(null))).isInstanceOf(RuntimeException.class);}finally{jdbc.execute("DROP TRIGGER m26_fail_invite");}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transport_operators WHERE code=?",Long.class,"ONB-"+id)).isZero();
        assertThat(onboarding.detail(admin,id).get("status")).isEqualTo("UNDER_REVIEW");
    }
    @Test void suspensionWinsBeforeWaitingBookingChecksOperator() throws Exception {
        var f=tx.execute(s->fixture());var owner=tx.execute(s->customer("m26-race"));var held=hold(owner,f,0,2,0);
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var suspension=pool.submit(()->tx.execute(s->{jdbc.queryForObject("SELECT id FROM transport_operators WHERE id=? FOR UPDATE",Long.class,f.operator().getId());locked.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){throw new RuntimeException(e);}return onboarding.status(admin,f.operator().getId(),new Reason("Review"),true);}));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            var booking=pool.submit(()->{try{bookingService.create(owner.user(),new com.busgo.booking.BookingDtos.CreateBookingRequest(held.holdToken(),"User","0901234567",null));return "OK";}catch(BusinessException e){return e.getCode();}});
            release.countDown();suspension.get(10,TimeUnit.SECONDS);assertThat(booking.get(10,TimeUnit.SECONDS)).isNotEqualTo("OK");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE trip_id=?",Long.class,f.trip().getId())).isZero();
        } finally {
            release.countDown();pool.shutdownNow();jdbc.update("DELETE FROM operator_onboarding_history WHERE operator_id=?",f.operator().getId());NotificationTestCleanup.fixture(jdbc,f);
            jdbc.update("DELETE FROM user_roles WHERE user_id=?",owner.user().id());jdbc.update("DELETE FROM users WHERE id=?",owner.user().id());
        }
    }
    @AfterEach void cleanup() {
        tx.executeWithoutResult(s->{for(long id:applications) {
            var op=jdbc.queryForObject("SELECT operator_id FROM operator_applications WHERE id=?",Long.class,id);
            var users=op==null?List.<Long>of():jdbc.queryForList("SELECT user_id FROM operator_staff WHERE operator_id=?",Long.class,op);
            jdbc.update("DELETE d FROM notification_deliveries d JOIN notifications n ON n.id=d.notification_id WHERE n.recipient_key=?","application:"+id);
            jdbc.update("DELETE FROM notifications WHERE recipient_key=?","application:"+id);
            jdbc.update("DELETE FROM operator_onboarding_history WHERE application_id=? OR operator_id=?",id,op);
            jdbc.update("DELETE FROM operator_admin_invitations WHERE application_id=?",id);
            jdbc.update("DELETE FROM operator_applications WHERE id=?",id);
            if(op!=null){jdbc.update("DELETE FROM operator_staff WHERE operator_id=?",op);jdbc.update("DELETE FROM transport_operators WHERE id=?",op);}
            for(long uid:users){jdbc.update("DELETE FROM user_roles WHERE user_id=?",uid);jdbc.update("DELETE FROM users WHERE id=?",uid);}
        }jdbc.update("DELETE FROM user_roles WHERE user_id=?",admin.id());jdbc.update("DELETE FROM users WHERE id=?",admin.id());});
    }
}
