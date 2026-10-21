package com.busgo;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.busgo.booking.*;
import com.busgo.finance.*;
import com.busgo.finance.FinanceDtos.*;
import com.busgo.finance.FinanceDtos.Period;
import com.busgo.common.security.CurrentUser;
import com.busgo.user.entity.RoleCode;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @ActiveProfiles("dev") @Transactional @AutoConfigureMockMvc
class M27AFinanceIT extends M20Support {
    @Autowired FinanceService finance;
    @Autowired FinancialLedger ledger;
    @Autowired CancellationService cancellations;
    @Autowired PartialCancellationService partial;
    @Autowired MockMvc mvc;
    @Autowired com.busgo.promotion.VoucherService vouchers;
    Fixture f;UserAuth owner;CurrentUser platform,admin;
    Period period;
    @BeforeEach void setup() {
        f=expanded();owner=customer("m27a");platform=actor(f,RoleCode.SYSTEM_ADMIN);admin=actor(f,RoleCode.OPERATOR_ADMIN);
        var day=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));period=new Period(day.minusDays(1),day.plusDays(1));authenticate(owner.user());
    }
    @AfterEach void clear() {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    long policy(String rate) {
        // Historical fixture policy, never a production backfill operation.
        var before=LocalDateTime.now(ZoneOffset.UTC).minusDays(2);
        jdbc.update("INSERT INTO commission_policies(operator_id,rate,valid_from,state,created_by,reason,created_at,activated_at) VALUES(?,?,?,'ACTIVE',?,'test',?,?)",f.operator().getId(),new BigDecimal(rate),before,platform.id(),before,before);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
    }
    List<Event> events() {return finance.events(platform,true,f.operator().getId(),period,0,100).data();}
    Run reconcile(boolean project) {return finance.reconcile(platform,new RunInput(f.operator().getId(),period.from(),period.to(),project));}
    @Test void pendingExcludedThenUnpricedAndRetriedExactlyOnce() {
        long b=web(f,owner,0).bookingId();assertThat(events()).isEmpty();
        paymentService.confirm(owner.user(),b);paymentService.confirm(owner.user(),b);ledger.postBooking(b);
        assertThat(events()).hasSize(1);assertThat(events().get(0).commission()).isNull();
        assertThat(events().get(0).operatorPayable()).isNull();
        assertThat(reconcile(true).exceptionCount()).isOne();assertThat(reconcile(true).projectedCount()).isZero();
    }
    @Test void pricedCollectionAndSeveralRefundsExactlyReverseOriginal() {
        policy("3.3333");long b=web(f,owner,0,1,2).bookingId();paymentService.confirm(owner.user(),b);
        var original=events().get(0);assertThat(original.commission()).isEqualByComparingTo("20.00");
        var ids=itemIds(b);
        partial.execute(owner.user(),b,false,new PartialCancellationDtos.Request(List.of(ids.get(0))));
        partial.execute(owner.user(),b,false,new PartialCancellationDtos.Request(List.of(ids.get(1))));
        cancellations.customerCancel(owner.user(),b,new CancellationDtos.Request(null));
        cancellations.customerCancel(owner.user(),b,new CancellationDtos.Request(null));ledger.postBooking(b);
        assertThat(events()).hasSize(4);
        assertThat(events().stream().map(Event::commission).reduce(BigDecimal.ZERO,BigDecimal::add)).isZero();
        assertThat(events().stream().map(Event::amount).reduce(BigDecimal.ZERO,BigDecimal::add)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE booking_id=?",String.class,b)).isEqualTo("REFUNDED");
        assertThat(reconcile(false).status()).isEqualTo("CLEAN");
    }
    @Test void zeroPolicyIsPricedAndStatementImmutable() {
        policy("0");long b=web(f,owner,0).bookingId();paymentService.confirm(owner.user(),b);
        assertThat(events().get(0).commission()).isZero();assertThat(events().get(0).operatorPayable()).isEqualByComparingTo("200");
        var run=reconcile(false);var statement=finance.issue(platform,run.id());assertThat(finance.issue(platform,run.id()).id()).isEqualTo(statement.id());
        cancellations.customerCancel(owner.user(),b,new CancellationDtos.Request(null));
        assertThat(finance.statement(platform,true,null,statement.id()).snapshot().totals().refunds()).isZero();
        assertThatThrownBy(()->jdbc.update("UPDATE financial_statements SET snapshot='{}' WHERE id=?",statement.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM financial_events WHERE payment_id=?",events().get(0).paymentId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void futurePolicyDoesNotRepriceCollectionAndOverlapRejected() {
        long b=web(f,owner,0).bookingId();paymentService.confirm(owner.user(),b);
        var start=OffsetDateTime.now(ZoneOffset.UTC).plusHours(1).withNano(0);
        var p=finance.createPolicy(platform,new PolicyInput(f.operator().getId(),new BigDecimal("5"),start,null,"Contract"));
        finance.activate(platform,p.id());ledger.postBooking(b);assertThat(events().get(0).policyId()).isNull();
        var overlap=finance.createPolicy(platform,new PolicyInput(f.operator().getId(),new BigDecimal("10"),start,null,"Conflict"));
        assertThatThrownBy(()->finance.activate(platform,overlap.id())).isInstanceOf(com.busgo.common.exception.BusinessException.class);
        var replacement=finance.createPolicy(platform,new PolicyInput(f.operator().getId(),new BigDecimal("7"),start.plusHours(1),null,"Replace"));
        finance.activate(platform,replacement.id());
        assertThat(finance.policies(platform,true,f.operator().getId(),0,20).data().stream().filter(x->x.id()==p.id()).findFirst().orElseThrow().validTo()).isEqualTo(start.plusHours(1).withNano((start.getNano()/1000)*1000));
    }
    @Test void phoneCollectionHasReceivableAndNoPlatformPayable() {
        policy("5");authenticate(admin);
        var b=assisted.create(admin,request(f,0,2,com.busgo.payment.entity.PaymentMethod.PAY_ON_BOARD));
        assisted.record(admin,b.bookingId(),new AssistedBookingDtos.RecordPayment(com.busgo.payment.entity.PaymentMethod.PAY_ON_BOARD,"Cash simulation"));
        var e=events().get(0);assertThat(e.channel()).isEqualTo("OPERATOR_RECORDED_SIMULATED");
        assertThat(e.operatorPayable()).isZero();assertThat(e.platformReceivable()).isEqualByComparingTo("10");
    }
    @Test void tenantRolesAndNoFinanceCommerceMutation() throws Exception {
        var other=fixture();var foreign=actor(other,RoleCode.OPERATOR_ADMIN);var staff=actor(f,RoleCode.OPERATOR_STAFF);
        String path="/api/v1/operator/finance/overview?from="+period.from()+"&to="+period.to();
        for(var denied:List.of(owner.user(),staff,platform)) mvc.perform(get(path).header("Authorization",bearer(denied))).andExpect(status().isForbidden());
        mvc.perform(get(path+"&operatorId="+f.operator().getId()).header("Authorization",bearer(foreign))).andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization",bearer(admin))).andExpect(status().isOk());
        var mixed=new CurrentUser(platform.id(),List.of(RoleCode.SYSTEM_ADMIN,RoleCode.OPERATOR_ADMIN));
        mvc.perform(get(path).header("Authorization",bearer(mixed))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/operator/finance/operators").header("Authorization",bearer(mixed))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/operator/finance/reconciliations").header("Authorization",bearer(mixed)).contentType("application/json")
            .content("{\"operatorId\":"+f.operator().getId()+",\"from\":\""+period.from()+"\",\"to\":\""+period.to()+"\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/finance/payments/1/refund").header("Authorization",bearer(platform))).andExpect(status().isNotFound());
    }
    @Test void discountedNetCollectionAndRefundUsePersistedPrice() {
        policy("5");authenticate(admin);
        var terms=new com.busgo.promotion.VoucherDtos.Terms("M27","Discount",null,"PERCENT",new BigDecimal("25"),null,BigDecimal.ZERO,
            OffsetDateTime.now().minusDays(1),OffsetDateTime.now().plusDays(10),null,null,null);
        long voucher=((Number)vouchers.create(admin,terms).get("id")).longValue();vouchers.state(admin,voucher,true);
        authenticate(owner.user());var hold=hold(owner,f,0,2,0,1);
        long b=bookingService.create(owner.user(),new BookingDtos.CreateBookingRequest(hold.holdToken(),"Customer","0901234567",null,"M27")).bookingId();
        assertThat(events()).isEmpty();paymentService.confirm(owner.user(),b);
        var event=events().get(0);assertThat(event.amount()).isEqualByComparingTo("300");assertThat(event.commission()).isEqualByComparingTo("15");
        assertThat(event.grossContext()).isEqualByComparingTo("400");assertThat(event.discountContext()).isEqualByComparingTo("100");
        partial.execute(owner.user(),b,false,new PartialCancellationDtos.Request(List.of(itemIds(b).get(0))));
        assertThat(events().get(0).commission()).isEqualByComparingTo("-7.50");assertThat(reconcile(false).status()).isEqualTo("CLEAN");
    }
    @Test void m20AdjustmentAndRefundAreDistinctAndSeatChangeHasNoCashFlow() {
        policy("5");long b=web(f,owner,0).bookingId();paymentService.confirm(owner.user(),b);
        var seat=modifications.create(owner.user(),b,false,seatRequest(b,f,2));modifications.confirm(owner.user(),b,false,seat.id());assertThat(events()).hasSize(1);
        var target=targetTrip(f);var fare=f.fares().stream().filter(x->x.getPrice().compareTo(new BigDecimal("200"))==0).findFirst().orElseThrow();
        fare.setPrice(new BigDecimal("300"));fares.saveAndFlush(fare);
        var move=modifications.create(owner.user(),b,false,tripRequest(b,target));modifications.confirm(owner.user(),b,false,move.id());modifications.confirm(owner.user(),b,false,move.id());
        assertThat(events()).hasSize(2);assertThat(events().get(0).purpose()).isEqualTo("BOOKING_MODIFICATION");assertThat(events().get(0).commission()).isEqualByComparingTo("5");
        fare.setPrice(new BigDecimal("100"));fares.saveAndFlush(fare);
        var back=modifications.create(owner.user(),b,false,tripRequest(b,f.trip()));modifications.confirm(owner.user(),b,false,back.id());
        assertThat(events().stream().map(Event::amount).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("100");
        assertThat(events().stream().map(Event::commission).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("5");assertThat(reconcile(false).status()).isEqualTo("CLEAN");
    }
    @Test void phoneQrIsSimulatedAndAssistedModificationIsUnknown() {
        policy("5");authenticate(admin);var b=assisted.create(admin,request(f,0,2,com.busgo.payment.entity.PaymentMethod.QR_TRANSFER));
        var link=assisted.issueLink(admin,b.bookingId());assisted.publicConfirm(token(link));
        assertThat(events().get(0).channel()).isEqualTo("OPERATOR_RECORDED_SIMULATED");
        var target=targetTrip(f);var fare=f.fares().stream().filter(x->x.getPrice().compareTo(new BigDecimal("200"))==0).findFirst().orElseThrow();fare.setPrice(new BigDecimal("300"));fares.saveAndFlush(fare);
        var move=modifications.create(admin,b.bookingId(),true,tripRequest(b.bookingId(),target));modifications.confirm(admin,b.bookingId(),true,move.id());
        var adjustment=events().get(0);assertThat(adjustment.channel()).isEqualTo("UNKNOWN_OR_UNVERIFIED");assertThat(adjustment.operatorPayable()).isNull();assertThat(adjustment.platformReceivable()).isNull();
        assertThat(reconcile(false).status()).isEqualTo("EXCEPTIONS");
    }
    long legacyPayment(long booking) {
        var p=new com.busgo.payment.entity.Payment();p.setBooking(em.find(com.busgo.booking.entity.Booking.class,booking));
        p.setAmount(new BigDecimal("200"));p.setMethod(com.busgo.payment.entity.PaymentMethod.MOCK_ONLINE);p.setStatus(com.busgo.payment.entity.PaymentStatus.PAID);
        p.setPaidAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(1));p.setTransactionReference("LEGACY-"+UUID.randomUUID());em.persist(p);em.flush();return p.getId();
    }
    @Test void legacyMissingIsVisibleAndBackfillIsIdempotentWithoutInventedRate() {
        long b=web(f,owner,0).bookingId();long payment=legacyPayment(b);
        var sourceBefore=jdbc.queryForMap("SELECT * FROM payments WHERE id=?",payment);
        assertThat(finance.overview(platform,true,f.operator().getId(),period).totals().missingCount()).isOne();
        assertThat(reconcile(false).exceptionCount()).isOne();
        assertThat(reconcile(true).projectedCount()).isOne();assertThat(reconcile(true).projectedCount()).isZero();
        assertThat(events().get(0).commission()).isNull();assertThat(jdbc.queryForMap("SELECT * FROM payments WHERE id=?",payment)).isEqualTo(sourceBefore);
    }
    @Test void mismatchedAppendOnlyProjectionIsReportedNotOverwritten() {
        long b=web(f,owner,0).bookingId();long payment=legacyPayment(b);
        jdbc.update("INSERT INTO financial_events(source_key,event_type,operator_id,booking_id,payment_id,purpose,origin,channel,amount,occurred_at,created_at) VALUES(?,'COLLECTION',?,?,?,'BOOKING','WEB','PLATFORM_MEDIATED_SIMULATED',199,?,?)",
            "payment:"+payment,f.operator().getId(),b,payment,LocalDateTime.now(ZoneOffset.UTC),LocalDateTime.now(ZoneOffset.UTC));
        var run=reconcile(false);assertThat(finance.exceptions(platform,true,null,run.id(),0,20).data()).extracting(ExceptionItem::reason).contains("SOURCE_AMOUNT_OR_TIME_MISMATCH");
        assertThatThrownBy(()->jdbc.update("UPDATE financial_events SET amount=200 WHERE payment_id=?",payment)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("INSERT INTO financial_events(source_key,event_type,operator_id,booking_id,payment_id,purpose,origin,channel,amount,occurred_at,created_at) VALUES('different-key','COLLECTION',?,?,?,'BOOKING','WEB','PLATFORM_MEDIATED_SIMULATED',200,?,?)",f.operator().getId(),b,payment,LocalDateTime.now(),LocalDateTime.now())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void reconciliationDetectsWrongPolicyAndRefundSnapshots() {
        long policy=policy("5");long b=web(f,owner,0).bookingId();long payment=legacyPayment(b);
        var at=jdbc.queryForObject("SELECT paid_at FROM payments WHERE id=?",(r,n)->com.busgo.common.time.JpaJdbcTime.read(r,"paid_at"),payment);
        jdbc.update("INSERT INTO financial_events(source_key,event_type,operator_id,booking_id,payment_id,purpose,origin,channel,amount,policy_id,rate,commission,operator_payable,platform_receivable,occurred_at,created_at) VALUES(?,'COLLECTION',?,?,?,'BOOKING','WEB','PLATFORM_MEDIATED_SIMULATED',200,?,10,20,180,0,?,?)",
            "payment:"+payment,f.operator().getId(),b,payment,policy,at,LocalDateTime.now(ZoneOffset.UTC));
        var now=LocalDateTime.now(ZoneOffset.UTC);
        // A refund without a partial-cancellation/modification link must consume the full balance.
        jdbc.update("INSERT INTO refunds(payment_id,amount,payment_paid_at,refunded_at,refunded_by,reason_code,created_at) SELECT id,200,paid_at,?,?,'CUSTOMER_CANCELLED',? FROM payments WHERE id=?",
            com.busgo.common.time.JpaJdbcTime.parameter(now),owner.user().id(),com.busgo.common.time.JpaJdbcTime.parameter(now),payment);
        long refund=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        jdbc.update("INSERT INTO financial_events(source_key,event_type,operator_id,booking_id,payment_id,refund_id,purpose,origin,channel,amount,policy_id,rate,commission,operator_payable,platform_receivable,occurred_at,created_at) VALUES(?,'REFUND',?,?,?,?,'BOOKING','WEB','PLATFORM_MEDIATED_SIMULATED',-200,?,10,-10,-190,0,?,?)",
            "refund:"+refund,f.operator().getId(),b,payment,refund,policy,now,now);
        var run=reconcile(false);
        assertThat(finance.exceptions(platform,true,null,run.id(),0,20).data()).extracting(ExceptionItem::reason).contains("POLICY_SNAPSHOT_MISMATCH","REFUND_ALLOCATION_MISMATCH");
    }
}
