package com.busgo;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.busgo.booking.*;
import com.busgo.common.exception.BusinessException;
import com.busgo.common.security.CurrentUser;
import com.busgo.promotion.*;
import com.busgo.promotion.VoucherDtos.*;
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

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev") @Transactional
class M30VoucherOptionsIT extends M20Support {
    @Autowired VoucherService vouchers;
    @Autowired MockMvc mvc;
    Fixture f; CurrentUser admin; UserAuth owner; long voucher;
    Terms terms(String code,BigDecimal minimum,Integer total,Integer per,Long route) {
        return new Terms(code,"Public offer","Approved public description","PERCENT",new BigDecimal("25"),new BigDecimal("50"),minimum,OffsetDateTime.now().minusDays(1),OffsetDateTime.now().plusDays(1),total,per,route);
    }
    long offer(CurrentUser a,Terms t,boolean visible) {
        authenticate(a);long id=((Number)vouchers.create(a,t).get("id")).longValue();vouchers.state(a,id,true);
        if(visible)vouchers.visibility(a,id,true);return id;
    }
    Options options(String token) {authenticate(owner.user());return bookingService.voucherOptions(owner.user(),new OptionsRequest(token,0,20));}
    @BeforeEach void setup() {f=expanded();admin=actor(f,RoleCode.OPERATOR_ADMIN);owner=customer("m30-options");voucher=offer(admin,terms("PUBLIC25",BigDecimal.ZERO,10,1,null),false);}
    @AfterEach void clear() {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    @Test void privateDefaultAndVisibilityIndependentOfManualQuoteAndUsageHistory() {
        authenticate(owner.user());var h=hold(owner,f,0,2,0);
        assertThat(options(h.holdToken()).options()).isEmpty();
        assertThat(bookingService.voucherQuote(owner.user(),new QuoteRequest(h.holdToken(),"PUBLIC25")).discountAmount()).isEqualByComparingTo("50");
        authenticate(admin);vouchers.visibility(admin,voucher,true);
        assertThat(options(h.holdToken()).options()).hasSize(1);
        var booking=bookingService.create(owner.user(),new BookingDtos.CreateBookingRequest(h.holdToken(),"C","0901234567",null,"PUBLIC25"));
        authenticate(admin);assertThat(vouchers.detail(admin,voucher).get("editable")).isEqualTo(false);
        vouchers.visibility(admin,voucher,false);vouchers.visibility(admin,voucher,true);
        assertThat(jdbc.queryForObject("SELECT discount_amount FROM bookings WHERE id=?",BigDecimal.class,booking.bookingId())).isEqualByComparingTo("50");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_usages WHERE voucher_id=?",Long.class,voucher)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_management_history WHERE voucher_id=? AND event='PUBLIC_ENABLED'",Long.class,voucher)).isEqualTo(2);
    }
    @Test void optionsUseSameQuoteEngineAndNeverReserveOrExposeUsage() throws Exception {
        authenticate(admin);vouchers.visibility(admin,voucher,true);authenticate(owner.user());var h=hold(owner,f,0,2,0,1);
        var result=options(h.holdToken());var option=result.options().get(0);
        assertThat(result.grossAmount()).isEqualByComparingTo("400");assertThat(result.seatCount()).isEqualTo(2);
        assertThat(option.status()).isEqualTo(OptionStatus.AVAILABLE);
        assertThat(option.quote()).isEqualTo(bookingService.voucherQuote(owner.user(),new QuoteRequest(h.holdToken(),option.code())));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_usages WHERE voucher_id=?",Long.class,voucher)).isZero();
        String body=mvc.perform(post("/api/v1/bookings/voucher-options").header("Authorization",bearer(owner.user())).contentType("application/json").content("{\"holdToken\":\""+h.holdToken()+"\"}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store")).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("created_by","updated_by","customer_id","reserved_at","redeemed","usage","operator_id","createdAt");
    }
    @Test void visibilityAndOptionsEnforceRolesOwnerAndTenant() throws Exception {
        var staff=actor(f,RoleCode.OPERATOR_STAFF);var foreign=actor(fixture(),RoleCode.OPERATOR_ADMIN);
        for(var a:List.of(staff,owner.user())) mvc.perform(patch("/api/v1/operator/vouchers/"+voucher+"/visibility").header("Authorization",bearer(a)).contentType("application/json").content("{\"customerVisible\":true}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/operator/vouchers/"+voucher+"/visibility").header("Authorization",bearer(foreign)).contentType("application/json").content("{\"customerVisible\":true}")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/operator/vouchers/"+voucher+"/visibility").header("Authorization",bearer(admin)).contentType("application/json").content("{\"customerVisible\":true}")).andExpect(status().isOk()).andExpect(jsonPath("data.customer_visible").value(true));
        mvc.perform(get("/api/v1/operator/vouchers/"+voucher).header("Authorization",bearer(staff))).andExpect(status().isOk());
        authenticate(owner.user());var h=hold(owner,f,0,2,0);var other=customer("m30-other");
        for(var a:List.of(staff,admin,actor(f,RoleCode.SYSTEM_ADMIN))) mvc.perform(post("/api/v1/bookings/voucher-options").header("Authorization",bearer(a)).contentType("application/json").content("{\"holdToken\":\""+h.holdToken()+"\"}" )).andExpect(status().isForbidden());
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        mvc.perform(post("/api/v1/bookings/voucher-options").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()).contentType("application/json").content("{\"holdToken\":\"x\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/bookings/voucher-options").header("Authorization",bearer(other.user())).contentType("application/json").content("{\"holdToken\":\""+h.holdToken()+"\"}" )).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/bookings/voucher-options").header("Authorization",bearer(owner.user())).contentType("application/json").content("{\"holdToken\":\"x\",\"size\":1000}" )).andExpect(status().isBadRequest());
    }
    @Test void inactiveFutureExpiredPrivateAndOtherRouteNeverAppear() {
        authenticate(admin);vouchers.visibility(admin,voucher,true);
        long paused=offer(admin,terms("PAUSED",BigDecimal.ZERO,null,null,null),true);vouchers.state(admin,paused,false);
        long future=offer(admin,terms("FUTURE",BigDecimal.ZERO,null,null,null),true);jdbc.update("UPDATE operator_vouchers SET starts_at=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 1 DAY),ends_at=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 2 DAY) WHERE id=?",future);
        long expired=offer(admin,terms("EXPIRED",BigDecimal.ZERO,null,null,null),true);jdbc.update("UPDATE operator_vouchers SET starts_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 2 DAY),ends_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 DAY) WHERE id=?",expired);
        long draft=((Number)vouchers.create(admin,terms("DRAFT",BigDecimal.ZERO,null,null,null)).get("id")).longValue();vouchers.visibility(admin,draft,true);
        offer(admin,terms("PRIVATE",BigDecimal.ZERO,null,null,null),false);
        var other=fixture();var otherAdmin=actor(other,RoleCode.OPERATOR_ADMIN);offer(otherAdmin,terms("PUBLIC25",BigDecimal.ZERO,null,null,null),true);
        var route=operatorRoutes.findById(f.operatorRoute().getId()).orElseThrow();
        // Same tenant, a different operator-route association is never a route match.
        var alternate=fixture();alternate.operatorRoute().setOperator(f.operator());operatorRoutes.saveAndFlush(alternate.operatorRoute());
        offer(admin,terms("OTHERROUTE",BigDecimal.ZERO,null,null,alternate.operatorRoute().getId()),true);
        authenticate(owner.user());var h=hold(owner,f,0,2,0);
        assertThat(options(h.holdToken()).options()).extracting(Option::code).containsExactly("PUBLIC25");
    }
    @Test void minimumSpendAndCustomerQuotaAreExplainedWithoutReservation() {
        authenticate(admin);vouchers.visibility(admin,voucher,true);offer(admin,terms("MIN500",new BigDecimal("500"),null,null,null),true);
        authenticate(owner.user());var first=hold(owner,f,0,2,0);var second=hold(owner,f,0,2,1);
        assertThat(options(second.holdToken()).options()).filteredOn(v->v.code().equals("MIN500")).extracting(Option::status).containsExactly(OptionStatus.CONDITION_NOT_MET);
        bookingService.create(owner.user(),new BookingDtos.CreateBookingRequest(first.holdToken(),"C","0901234567",null,"PUBLIC25"));
        assertThat(options(second.holdToken()).options()).filteredOn(v->v.code().equals("PUBLIC25")).extracting(Option::status).containsExactly(OptionStatus.UNAVAILABLE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_usages WHERE voucher_id=?",Long.class,voucher)).isOne();
    }
    @Test void expiredCampaignBetweenListAndCreateRejectsWithoutConsumingHold() {
        authenticate(admin);vouchers.visibility(admin,voucher,true);authenticate(owner.user());var h=hold(owner,f,0,2,0);
        assertThat(options(h.holdToken()).options().get(0).status()).isEqualTo(OptionStatus.AVAILABLE);
        jdbc.update("UPDATE operator_vouchers SET starts_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 2 DAY),ends_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 DAY) WHERE id=?",voucher);
        assertThatThrownBy(()->bookingService.create(owner.user(),new BookingDtos.CreateBookingRequest(h.holdToken(),"C","0901234567",null,"PUBLIC25"))).isInstanceOf(BusinessException.class).extracting(e->((BusinessException)e).getCode()).isEqualTo("VOUCHER_EXPIRED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_usages WHERE voucher_id=?",Long.class,voucher)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory WHERE hold_token=?",Long.class,h.holdToken())).isPositive();
    }
    @Test void expiredHoldStopsOptionsAndSuspendedOperatorCannotOfferVouchers() {
        authenticate(owner.user());var h=hold(owner,f,0,2,0);
        jdbc.update("UPDATE trip_seat_segment_inventory SET hold_expires_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 MINUTE) WHERE hold_token=?",h.holdToken());
        assertThatThrownBy(()->options(h.holdToken())).isInstanceOf(BusinessException.class).extracting(e->((BusinessException)e).getCode()).isEqualTo("SEAT_HOLD_EXPIRED");
        var next=hold(owner,f,0,2,1);f.operator().setStatus(com.busgo.operator.entity.OperatorStatus.SUSPENDED);em.flush();
        assertThatThrownBy(()->options(next.holdToken())).isInstanceOf(BusinessException.class).extracting(e->((BusinessException)e).getCode()).isEqualTo("TRIP_NOT_BOOKABLE");
    }
    @Test void boundedPaginationKeepsSafeOptionsDistinct() {
        authenticate(admin);vouchers.visibility(admin,voucher,true);offer(admin,terms("SECOND",BigDecimal.ZERO,null,null,null),true);
        authenticate(owner.user());var h=hold(owner,f,0,2,0);
        var first=bookingService.voucherOptions(owner.user(),new OptionsRequest(h.holdToken(),0,1));
        var second=bookingService.voucherOptions(owner.user(),new OptionsRequest(h.holdToken(),1,1));
        assertThat(first.pagination().totalPages()).isEqualTo(2);assertThat(first.options().get(0).id()).isNotEqualTo(second.options().get(0).id());
    }
}
