package com.busgo;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import com.busgo.booking.*;
import com.busgo.common.security.CurrentUser;
import com.busgo.finance.*;
import com.busgo.finance.FinanceDtos.*;
import com.busgo.user.entity.RoleCode;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties={"busgo.notifications.initial-delay=PT24H","busgo.booking.payment-expiry-initial-delay=PT24H"})
@ActiveProfiles("dev")
class M27AConcurrencyIT extends M20Support {
    @Autowired TransactionTemplate tx;
    @Autowired FinanceService finance;
    @Autowired FinancialLedger ledger;
    @Autowired CancellationService cancellations;
    @MockitoBean Clock clock;
    Fixture f;UserAuth owner;CurrentUser platform;long booking;Instant time;
    @BeforeEach void setup() {
        time=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);when(clock.instant()).thenReturn(time);when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        tx.executeWithoutResult(s->{f=fixture();owner=customer("m27-race");platform=actor(f,RoleCode.SYSTEM_ADMIN);authenticate(owner.user());booking=web(f,owner,0).bookingId();});
    }
    @AfterEach void clear() {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    RunInput input() {var date=time.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();return new RunInput(f.operator().getId(),date.minusDays(1),date.plusDays(1),true);}
    void race(Runnable first,Runnable second) throws Exception {
        var gate=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        try {var a=pool.submit(()->attempt(gate,first));var b=pool.submit(()->attempt(gate,second));a.get(30,TimeUnit.SECONDS);b.get(30,TimeUnit.SECONDS);}
        finally{pool.shutdownNow();}
    }
    void attempt(CyclicBarrier gate,Runnable action) {authenticate(owner.user());try{gate.await(10,TimeUnit.SECONDS);action.run();}catch(Exception e){throw new RuntimeException(e);}finally{clear();}}
    long count() {return jdbc.queryForObject("SELECT COUNT(*) FROM financial_events WHERE booking_id=?",Long.class,booking);}
    @Test void doubleConfirmationAndConcurrentReconciliationNeverDuplicate() throws Exception {
        race(()->paymentService.confirm(owner.user(),booking),()->paymentService.confirm(owner.user(),booking));assertThat(count()).isOne();
        race(()->finance.reconcile(platform,input()),()->finance.reconcile(platform,input()));assertThat(count()).isOne();
    }
    @Test void refundVersusProjectionKeepsCollectionAndRefundOnce() throws Exception {
        paymentService.confirm(owner.user(),booking);
        race(()->cancellations.customerCancel(owner.user(),booking,new CancellationDtos.Request(null)),()->finance.reconcile(platform,input()));
        finance.reconcile(platform,input());assertThat(count()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM financial_events WHERE booking_id=?",BigDecimal.class,booking)).isZero();
    }
    @Test void rollbackLeavesNoSourceOrLedger() {
        assertThatThrownBy(()->tx.executeWithoutResult(s->{paymentService.confirm(owner.user(),booking);throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class);
        assertThat(count()).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE booking_id=?",Long.class,booking)).isZero();
        paymentService.confirm(owner.user(),booking);assertThat(count()).isOne();
    }
    @Test void paymentVersusPolicyActivationDoesNotRetroactivelyPrice() throws Exception {
        var p=finance.createPolicy(platform,new PolicyInput(f.operator().getId(),new BigDecimal("5"),time.plusSeconds(60).atOffset(ZoneOffset.UTC),null,"New contract"));
        race(()->paymentService.confirm(owner.user(),booking),()->finance.activate(platform,p.id()));
        assertThat(jdbc.queryForObject("SELECT commission FROM financial_events WHERE booking_id=?",BigDecimal.class,booking)).isNull();
    }
    @Test void replacementBoundaryUsesNewRateAndOldRefundSnapshot() {
        var start=time.plusSeconds(60).atOffset(ZoneOffset.UTC);var first=finance.createPolicy(platform,new PolicyInput(f.operator().getId(),new BigDecimal("5"),start,null,"First"));finance.activate(platform,first.id());
        var next=finance.createPolicy(platform,new PolicyInput(f.operator().getId(),new BigDecimal("10"),start.plusSeconds(60),null,"Replacement"));finance.activate(platform,next.id());
        when(clock.instant()).thenReturn(start.toInstant());paymentService.confirm(owner.user(),booking);
        assertThat(jdbc.queryForObject("SELECT rate FROM financial_events WHERE booking_id=?",BigDecimal.class,booking)).isEqualByComparingTo("5");
        when(clock.instant()).thenReturn(start.plusSeconds(60).toInstant());
        long second=tx.execute(s->web(f,owner,1).bookingId());paymentService.confirm(owner.user(),second);
        assertThat(jdbc.queryForObject("SELECT rate FROM financial_events WHERE booking_id=?",BigDecimal.class,second)).isEqualByComparingTo("10");
        cancellations.customerCancel(owner.user(),booking,new CancellationDtos.Request(null));
        assertThat(jdbc.queryForObject("SELECT SUM(commission) FROM financial_events WHERE booking_id=?",BigDecimal.class,booking)).isZero();
        assertThat(jdbc.queryForList("SELECT rate FROM financial_events WHERE booking_id=?",BigDecimal.class,booking)).allSatisfy(r->assertThat(r).isEqualByComparingTo("5"));
    }
    @Test void concurrentOverlappingActivationHasOneWinner() throws Exception {
        var start=time.plusSeconds(120).atOffset(ZoneOffset.UTC);
        var a=finance.createPolicy(platform,new PolicyInput(f.operator().getId(),new BigDecimal("5"),start,null,"A"));
        var b=finance.createPolicy(platform,new PolicyInput(f.operator().getId(),new BigDecimal("7"),start,null,"B"));
        var successes=new java.util.concurrent.atomic.AtomicInteger();
        race(()->activate(a.id(),successes),()->activate(b.id(),successes));assertThat(successes.get()).isOne();
    }
    void activate(long id,java.util.concurrent.atomic.AtomicInteger successes) {
        try{finance.activate(platform,id);successes.incrementAndGet();}catch(com.busgo.common.exception.BusinessException e){assertThat(e.getCode()).isEqualTo("COMMISSION_WINDOW_OVERLAP");}
    }
}
