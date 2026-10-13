package com.busgo;

import static org.assertj.core.api.Assertions.*;
import com.busgo.booking.*;
import com.busgo.common.exception.BusinessException;
import com.busgo.common.security.CurrentUser;
import com.busgo.promotion.*;
import com.busgo.promotion.VoucherDtos.*;
import com.busgo.user.entity.RoleCode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @ActiveProfiles("dev") @Transactional
class M24BPromotionsIT extends M20Support {
    @Autowired VoucherService vouchers;
    @Autowired CancellationService cancellations;
    @Autowired PartialCancellationService partial;
    Fixture f; CurrentUser admin; UserAuth owner; long voucher;
    Terms terms(String code,String type,String value,Integer total,Integer per) {
        return new Terms(code,"Summer",null,type,new BigDecimal(value),null,BigDecimal.ZERO,OffsetDateTime.now().minusDays(1),OffsetDateTime.now().plusDays(10),total,per,null);
    }
    @BeforeEach void setup() { f=expanded();admin=actor(f,RoleCode.OPERATOR_ADMIN);owner=customer("m24b");authenticate(admin);voucher=((Number)vouchers.create(admin,terms(" sale ","PERCENT","25",10,2)).get("id")).longValue();vouchers.state(admin,voucher,true); }
    @AfterEach void clear() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    BookingDtos.BookingResponse book(int... seats) { authenticate(owner.user());return bookingService.create(owner.user(),new BookingDtos.CreateBookingRequest(hold(owner,f,0,2,seats).holdToken(),"Customer","0901234567","m24b@example.test","sale")); }
    String state(long id) {return jdbc.queryForObject("SELECT state FROM voucher_usages WHERE booking_id=?",String.class,id);}
    void consistent(long id) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings b WHERE b.id=? AND b.total_amount=b.gross_amount-b.discount_amount AND b.total_amount=(SELECT SUM(unit_price) FROM booking_items WHERE booking_id=b.id AND cancelled=FALSE) AND b.discount_amount=(SELECT SUM(discount_amount) FROM booking_items WHERE booking_id=b.id AND cancelled=FALSE)",Long.class,id)).isOne();
    }
    @Test void previewReadOnlyThenReserveAndPayExactlyOnce() {
        authenticate(owner.user());var h=hold(owner,f,0,2,0,1);var q=bookingService.voucherQuote(owner.user(),new QuoteRequest(h.holdToken()," sale "));
        assertThat(q.grossAmount()).isEqualByComparingTo("400");assertThat(q.discountAmount()).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_usages WHERE voucher_id=?",Long.class,voucher)).isZero();
        var b=bookingService.create(owner.user(),new BookingDtos.CreateBookingRequest(h.holdToken(),"Customer","0901234567",null,"sale"));
        assertThat(state(b.bookingId())).isEqualTo("RESERVED");consistent(b.bookingId());
        paymentService.confirm(owner.user(),b.bookingId());paymentService.confirm(owner.user(),b.bookingId());
        assertThat(state(b.bookingId())).isEqualTo("REDEEMED");assertThat(count("payments",b.bookingId())).isOne();
        assertThat(jdbc.queryForObject("SELECT amount FROM payments WHERE booking_id=?",BigDecimal.class,b.bookingId())).isEqualByComparingTo("300");
    }
    @Test void normalizedUniqueWithinTenantAndSameCodeAcrossTenants() {
        assertThat(vouchers.detail(admin,voucher).get("code")).isEqualTo("SALE");
        assertThatThrownBy(()->vouchers.create(admin,terms("SaLe","FIXED_AMOUNT","10",null,null))).isInstanceOf(BusinessException.class);
        var other=fixture();var a=actor(other,RoleCode.OPERATOR_ADMIN);authenticate(a);assertThat(vouchers.create(a,terms("SALE","FIXED_AMOUNT","10",null,null)).get("code")).isEqualTo("SALE");
        assertThatThrownBy(()->vouchers.detail(a,voucher)).isInstanceOf(com.busgo.common.exception.ResourceNotFoundException.class);
    }
    @Test void staffAndSystemCannotMutate() {
        for(var role:List.of(RoleCode.OPERATOR_STAFF,RoleCode.SYSTEM_ADMIN)) {var a=actor(f,role);authenticate(a);assertThatThrownBy(()->vouchers.create(a,terms("NEW","FIXED_AMOUNT","10",null,null))).isInstanceOf(RuntimeException.class);}
    }
    @Test void pausedAfterReservationStillPaysAndTermsStayLocked() {
        var b=book(0);authenticate(admin);vouchers.state(admin,voucher,false);
        assertThatThrownBy(()->vouchers.edit(admin,voucher,terms("SALE","PERCENT","50",10,2))).isInstanceOf(BusinessException.class);
        authenticate(owner.user());paymentService.confirm(owner.user(),b.bookingId());assertThat(state(b.bookingId())).isEqualTo("REDEEMED");
    }
    @Test void unpaidCancellationReleasesButRetainsHistory() {
        var b=book(0);cancellations.customerCancel(owner.user(),b.bookingId(),new CancellationDtos.Request(null));
        cancellations.customerCancel(owner.user(),b.bookingId(),new CancellationDtos.Request(null));assertThat(state(b.bookingId())).isEqualTo("RELEASED");assertThat(count("voucher_usages",b.bookingId())).isOne();
    }
    @Test void expiryReleasesCapacity() {
        var b=book(0);var entity=em.find(com.busgo.booking.entity.Booking.class,b.bookingId());entity.setPaymentDueAt(com.busgo.common.time.BusGoTime.utc(java.time.Instant.now().minusSeconds(5)));em.flush();
        assertThat(cancellations.expire(b.bookingId())).isTrue();assertThat(state(b.bookingId())).isEqualTo("RELEASED");
    }
    @Test void paidFullCancellationRefundsNetAndRetainsRedemption() {
        var b=book(0,1);paymentService.confirm(owner.user(),b.bookingId());cancellations.customerCancel(owner.user(),b.bookingId(),new CancellationDtos.Request(null));
        assertThat(state(b.bookingId())).isEqualTo("REDEEMED");assertThat(jdbc.queryForObject("SELECT SUM(r.amount) FROM refunds r JOIN payments p ON p.id=r.payment_id WHERE p.booking_id=?",BigDecimal.class,b.bookingId())).isEqualByComparingTo("300");
    }
    @Test void partialCancellationRefundsDiscountedItems() {
        var b=book(0,1);paymentService.confirm(owner.user(),b.bookingId());
        var result=partial.execute(owner.user(),b.bookingId(),false,new PartialCancellationDtos.Request(List.of(itemIds(b.bookingId()).get(0))));
        assertThat(result.quote().refundRequired()).isEqualByComparingTo("150");consistent(b.bookingId());assertThat(state(b.bookingId())).isEqualTo("REDEEMED");
    }
    @Test void pendingPartialKeepsReservation() {
        var b=book(0,1);partial.execute(owner.user(),b.bookingId(),false,new PartialCancellationDtos.Request(List.of(itemIds(b.bookingId()).get(0))));consistent(b.bookingId());assertThat(state(b.bookingId())).isEqualTo("RESERVED");
    }
    @Test void seatChangePreservesDiscountAndOneRedemption() {
        var b=book(0);paymentService.confirm(owner.user(),b.bookingId());var m=modifications.create(owner.user(),b.bookingId(),false,seatRequest(b.bookingId(),f,2));
        modifications.confirm(owner.user(),b.bookingId(),false,m.id());modifications.confirm(owner.user(),b.bookingId(),false,m.id());consistent(b.bookingId());
        assertThat(jdbc.queryForObject("SELECT discount_amount FROM booking_items WHERE booking_id=?",BigDecimal.class,b.bookingId())).isEqualByComparingTo("50");assertThat(count("voucher_usages",b.bookingId())).isOne();
    }
    @Test void lowerFareCapsEntitlementAndLaterHigherFareRestoresIt() {
        var b=book(0);paymentService.confirm(owner.user(),b.bookingId());var target=targetTrip(f);
        var fare=f.fares().stream().filter(x->x.getPrice().compareTo(new BigDecimal("200"))==0).findFirst().orElseThrow();fare.setPrice(new BigDecimal("20"));fares.saveAndFlush(fare);
        var m=modifications.create(owner.user(),b.bookingId(),false,tripRequest(b.bookingId(),target));assertThat(m.quote().money().newTotal()).isEqualByComparingTo("0.01");
        modifications.confirm(owner.user(),b.bookingId(),false,m.id());consistent(b.bookingId());
        fare.setPrice(new BigDecimal("300"));fares.saveAndFlush(fare);
        var back=modifications.create(owner.user(),b.bookingId(),false,tripRequest(b.bookingId(),f.trip()));assertThat(back.quote().money().newTotal()).isEqualByComparingTo("250");modifications.confirm(owner.user(),b.bookingId(),false,back.id());consistent(b.bookingId());assertThat(count("voucher_usages",b.bookingId())).isOne();
    }
    @Test void perCustomerLimitIncludesPendingReservations() {
        book(0);book(1);assertThatThrownBy(()->book(2)).isInstanceOf(BusinessException.class).extracting(e->((BusinessException)e).getCode()).isEqualTo("VOUCHER_LIMIT_REACHED");
    }
    @Test void invalidCodeNeverConsumesHold() {
        authenticate(owner.user());var h=hold(owner,f,0,2,0);
        assertThatThrownBy(()->bookingService.create(owner.user(),new BookingDtos.CreateBookingRequest(h.holdToken(),"C","0901234567",null,"MISSING"))).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory WHERE hold_token=?",Long.class,h.holdToken())).isPositive();
    }
    @Test void noVoucherPersistsZeroDiscount() {authenticate(owner.user());var b=web(f,owner,0);consistent(b.bookingId());assertThat(b.price().discountAmount()).isZero();assertThat(count("voucher_usages",b.bookingId())).isZero();}
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"PAUSED","EXPIRED","FUTURE","MINIMUM","ROUTE","ZERO","FOREIGN","SUSPENDED"})
    void eligibilityFailsWithoutConsumingHold(String rule) {
        authenticate(owner.user());var h=hold(owner,f,0,2,0);authenticate(admin);
        switch(rule) {
            case "PAUSED" -> vouchers.state(admin,voucher,false);
            case "EXPIRED" -> jdbc.update("UPDATE operator_vouchers SET starts_at=?,ends_at=? WHERE id=?",com.busgo.common.time.JpaJdbcTime.parameter(com.busgo.common.time.BusGoTime.utc(java.time.Instant.now().minusSeconds(100))),com.busgo.common.time.JpaJdbcTime.parameter(com.busgo.common.time.BusGoTime.utc(java.time.Instant.now().minusSeconds(1))),voucher);
            case "FUTURE" -> jdbc.update("UPDATE operator_vouchers SET starts_at=? WHERE id=?",com.busgo.common.time.JpaJdbcTime.parameter(com.busgo.common.time.BusGoTime.utc(java.time.Instant.now().plusSeconds(100))),voucher);
            case "MINIMUM" -> jdbc.update("UPDATE operator_vouchers SET minimum_spend=1000 WHERE id=?",voucher);
            case "ZERO" -> jdbc.update("UPDATE operator_vouchers SET discount_value=100 WHERE id=?",voucher);
            case "ROUTE" -> {var other=fixture();jdbc.update("UPDATE operator_vouchers SET operator_route_id=? WHERE id=?",other.operatorRoute().getId(),voucher);}
            case "FOREIGN" -> {var other=fixture();jdbc.update("UPDATE operator_vouchers SET operator_id=? WHERE id=?",other.operator().getId(),voucher);}
            case "SUSPENDED" -> {f.operator().setStatus(com.busgo.operator.entity.OperatorStatus.SUSPENDED);operators.saveAndFlush(f.operator());}
        }
        authenticate(owner.user());
        assertThatThrownBy(()->bookingService.voucherQuote(owner.user(),new QuoteRequest(h.holdToken(),"SALE"))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->bookingService.create(owner.user(),new BookingDtos.CreateBookingRequest(h.holdToken(),"C","0901234567",null,"SALE"))).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE trip_id=?",Long.class,f.trip().getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_usages WHERE voucher_id=?",Long.class,voucher)).isZero();
    }
    @Test void fixedAmountAndPercentageCapPersistCorrectly() {
        vouchers.edit(admin,voucher,new Terms("SALE","Fixed",null,"FIXED_AMOUNT",new BigDecimal("75"),new BigDecimal("40"),BigDecimal.ZERO,OffsetDateTime.now().minusDays(1),OffsetDateTime.now().plusDays(1),10,2,f.operatorRoute().getId()));
        var b=book(0,1);assertThat(b.price().discountAmount()).isEqualByComparingTo("40");consistent(b.bookingId());
    }
    @Test void malformedAndForeignRouteTermsRejected() {
        assertThatThrownBy(()->vouchers.create(admin,terms("bad code","PERCENT","10",1,1))).isInstanceOf(BusinessException.class);
        var other=fixture();var t=terms("OTHER","PERCENT","10",1,1);
        assertThatThrownBy(()->vouchers.create(admin,new Terms(t.code(),t.name(),null,t.discountType(),t.discountValue(),null,t.minimumSpend(),t.startsAt(),t.endsAt(),1,1,other.operatorRoute().getId()))).isInstanceOf(com.busgo.common.exception.ResourceNotFoundException.class);
    }
    @Test void foreignCustomerCannotPreviewHold() {
        var h=hold(owner,f,0,2,0);var foreign=customer("foreign");authenticate(foreign.user());assertThatThrownBy(()->bookingService.voucherQuote(foreign.user(),new QuoteRequest(h.holdToken(),"SALE"))).isInstanceOf(com.busgo.common.exception.ResourceNotFoundException.class);
    }
}
