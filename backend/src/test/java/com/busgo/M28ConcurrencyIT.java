package com.busgo;

import static org.assertj.core.api.Assertions.*;
import com.busgo.common.security.CurrentUser;
import com.busgo.common.exception.BusinessException;
import com.busgo.support.*;
import com.busgo.support.SupportDtos.*;
import com.busgo.user.entity.RoleCode;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties="busgo.notifications.initial-delay=PT24H") @ActiveProfiles("dev")
class M28ConcurrencyIT extends M20Support {
    @Autowired TransactionTemplate tx;
    @Autowired SupportService support;
    @Autowired com.busgo.booking.CancellationService cancellations;
    Fixture f;UserAuth owner;CurrentUser admin,employee;long booking;Ticket ticket;
    @BeforeEach void setup() {
        tx.executeWithoutResult(s->{f=fixture();owner=customer("m28-race");booking=web(f,owner,0).bookingId();admin=actor(f,RoleCode.OPERATOR_ADMIN);employee=actor(f,RoleCode.OPERATOR_STAFF);});
        ticket=support.create(owner.user(),new Create(booking,Category.BAGGAGE,"Concurrent case","Nội dung","create"));
    }
    @AfterEach void cleanup() {
        long oid=f.operator().getId();String cases="SELECT id FROM support_tickets WHERE operator_id="+oid;
        jdbc.update("DELETE FROM support_notifications WHERE operator_id=? OR booking_id=?",oid,booking);
        jdbc.update("DELETE FROM support_activity WHERE ticket_id IN ("+cases+")");jdbc.update("DELETE FROM support_requests WHERE ticket_id IN ("+cases+")");
        jdbc.update("DELETE FROM support_disputes WHERE ticket_id IN ("+cases+")");jdbc.update("DELETE FROM support_tickets WHERE operator_id=?",oid);jdbc.update("DELETE FROM audit_events WHERE operator_id=?",oid);
        NotificationTestCleanup.fixture(jdbc,f);
        for(var u:List.of(admin,employee)) {jdbc.update("DELETE FROM user_roles WHERE user_id=?",u.id());jdbc.update("DELETE FROM users WHERE id=?",u.id());}
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
    Command cmd(String key) {return new Command(ticket.version(),key,"Nội dung",false,null,null,null,null);}
    List<String> race(Runnable a,Runnable b) throws Exception {
        var barrier=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        try {var one=pool.submit(()->attempt(barrier,a));var two=pool.submit(()->attempt(barrier,b));return List.of(one.get(30,TimeUnit.SECONDS),two.get(30,TimeUnit.SECONDS));}
        finally {pool.shutdownNow();}
    }
    String attempt(CyclicBarrier barrier,Runnable action) throws Exception {
        authenticate(employee);try{barrier.await(10,TimeUnit.SECONDS);action.run();return "OK";}catch(BusinessException e){return e.getCode();}
        finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }
    @Test void twoClaimsProduceOneWinnerAndNoLostVersion() throws Exception {
        assertThat(race(()->support.command(employee,"operator",ticket.id(),"claim",cmd("one")),()->support.command(employee,"operator",ticket.id(),"claim",cmd("two")))).containsExactlyInAnyOrder("OK","ACCESS_DENIED");
        authenticate(admin);assertThat(support.detail(admin,"operator",ticket.id()).version()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE resource_type='SUPPORT' AND resource_id=?",Long.class,ticket.id())).isEqualTo(2);
    }
    @Test void concurrentRetryCreatesExactlyOneActivityAndNotification() throws Exception {
        assertThat(race(()->support.command(employee,"operator",ticket.id(),"claim",cmd("same")),()->support.command(employee,"operator",ticket.id(),"claim",cmd("same")))).containsOnly("OK");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM support_activity WHERE ticket_id=? AND event_type='CLAIM'",Long.class,ticket.id())).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM support_notifications WHERE recipient_user_id=? AND event_key=?",Long.class,owner.user().id(),"support:"+ticket.id()+":1")).isOne();
    }
    @Test void rollbackRemovesBusinessActivityAuditAndNotification() {
        long audits=jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE operator_id=?",Long.class,f.operator().getId());
        assertThatThrownBy(()->tx.executeWithoutResult(s->{authenticate(admin);support.command(admin,"operator",ticket.id(),"resolve",cmd("rollback"));throw new IllegalStateException("forced rollback");})).isInstanceOf(IllegalStateException.class);
        authenticate(admin);assertThat(support.detail(admin,"operator",ticket.id()).status()).isEqualTo(State.OPEN);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE operator_id=?",Long.class,f.operator().getId())).isEqualTo(audits);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM support_activity WHERE ticket_id=?",Long.class,ticket.id())).isOne();
        assertThat(support.inbox(owner.user(),"customer",0,20).data()).isEmpty();
    }
    @Test void concurrentCreationRetryReturnsOneTicket() throws Exception {
        var c=new Create(booking,Category.BAGGAGE,"Retry create","Description","retry-create");
        assertThat(race(()->support.create(owner.user(),c),()->support.create(owner.user(),c))).containsOnly("OK");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM support_tickets WHERE customer_id=? AND request_key='retry-create'",Long.class,owner.user().id())).isOne();
    }
    @Test void competingCreationPayloadsCannotReuseOneRequestKey() throws Exception {
        var a=new Create(booking,Category.BAGGAGE,"First subject","Description","same-key");
        var b=new Create(booking,Category.BAGGAGE,"Other subject","Description","same-key");
        assertThat(race(()->support.create(owner.user(),a),()->support.create(owner.user(),b)))
            .containsExactlyInAnyOrder("OK","INVALID_SUPPORT_ACTION");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM support_tickets WHERE customer_id=? AND request_key='same-key'",Long.class,owner.user().id())).isOne();
    }
    @Test void caseCreationAndCommercialCancellationRemainIndependent() throws Exception {
        var c=new Create(booking,Category.BAGGAGE,"Cancellation race","Existing passenger support","cancel-race");
        assertThat(race(()->support.create(owner.user(),c),()->{
            authenticate(owner.user());
            cancellations.customerCancel(owner.user(),booking,new com.busgo.booking.CancellationDtos.Request("Customer cancellation"));
        })).containsOnly("OK");
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,booking)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM support_tickets WHERE customer_id=? AND request_key='cancel-race'",Long.class,owner.user().id())).isOne();
    }
}
