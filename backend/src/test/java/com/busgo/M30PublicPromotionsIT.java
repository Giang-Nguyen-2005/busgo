package com.busgo;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.busgo.booking.BookingDtos;
import com.busgo.common.exception.BusinessException;
import com.busgo.common.security.CurrentUser;
import com.busgo.promotion.*;
import com.busgo.promotion.VoucherDtos.*;
import com.busgo.user.entity.RoleCode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class M30PublicPromotionsIT extends M20Support {
    @Autowired VoucherService vouchers;
    @Autowired PublicPromotionService publicPromotions;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    Fixture f;CurrentUser admin;UserAuth owner;long voucher;
    long offer(CurrentUser actor,String code,Long route,Integer total,boolean visible) {
        authenticate(actor);
        var t=new Terms(code,"Public campaign","Approved text","PERCENT",new BigDecimal("25"),new BigDecimal("50"),new BigDecimal("500"),OffsetDateTime.now().minusDays(1),OffsetDateTime.now().plusDays(1),total,1,route);
        long id=((Number)vouchers.create(actor,t).get("id")).longValue();vouchers.state(actor,id,true);if(visible)vouchers.visibility(actor,id,true);return id;
    }
    @BeforeEach void setup(){f=expanded();admin=actor(f,RoleCode.OPERATOR_ADMIN);owner=customer("m304b");voucher=offer(admin,"PUBLIC25",null,10,false);}
    @AfterEach void cleanup(){org.springframework.security.test.context.TestSecurityContextHolder.clearContext();org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    List<PublicPromotionDtos.Promotion> list(){return publicPromotions.list(f.operator().getId(),0,20).data();}
    void publish(){authenticate(admin);vouchers.visibility(admin,voucher,true);}
    @Test void optInPublicDtoIsSameForGuestAndCustomerAndDoesNotQuoteOrReserve() throws Exception {
        assertThat(list()).isEmpty();publish();
        var response=mvc.perform(get("/api/v1/public/promotions").param("operatorId",f.operator().getId()+"").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();
        var customer=mvc.perform(get("/api/v1/public/promotions").param("operatorId",f.operator().getId()+"").header("Authorization",bearer(owner.user()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(response)).isEqualTo(json.readTree(customer));
        var dto=json.readTree(response).path("data").get(0);assertThat(dto.path("eligibility").asText()).isEqualTo("REQUIRES_CONTEXT");
        var fields=new HashSet<String>();dto.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id","operator","route","code","name","description","discountType","discountValue","maxDiscount","minimumSpend","startsAt","endsAt","customerLimit","eligibility");
        assertThat(response).doesNotContain("customer_id","created_by","updated_by","usage","reserved","totalAmount","discountAmount","password","taxCode");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_usages WHERE voucher_id=?",Long.class,voucher)).isZero();
    }
    @Test void tripAndOperatorScopeExcludeForeignAndWrongAssociation() {
        publish();var other=fixture();var foreign=actor(other,RoleCode.OPERATOR_ADMIN);offer(foreign,"PUBLIC25",null,null,true);
        var alternate=fixture();alternate.operatorRoute().setOperator(f.operator());operatorRoutes.saveAndFlush(alternate.operatorRoute());
        offer(admin,"WRONGROUTE",alternate.operatorRoute().getId(),null,true);
        offer(admin,"EXACTROUTE",f.operatorRoute().getId(),null,true);
        var result=publicPromotions.trip(f.trip().getId(),f.locations().get(0).getId(),f.locations().get(2).getId(),0,20);
        assertThat(result.data()).extracting(PublicPromotionDtos.Promotion::code).containsExactlyInAnyOrder("PUBLIC25","EXACTROUTE");
        assertThat(result.data()).allMatch(p->p.operator().id()==f.operator().getId() && p.eligibility()==PublicPromotionDtos.Eligibility.CONDITIONAL);
        assertThat(result.data()).filteredOn(p->p.code().equals("EXACTROUTE")).allMatch(p->p.route().id()==f.route().getId());
    }
    @Test void draftPausedFutureExpiredAndPrivateAreExcluded() {
        publish();long paused=offer(admin,"PAUSED",null,null,true);vouchers.state(admin,paused,false);
        long future=offer(admin,"FUTURE",null,null,true);jdbc.update("UPDATE operator_vouchers SET starts_at=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 1 DAY),ends_at=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 2 DAY) WHERE id=?",future);
        long expired=offer(admin,"EXPIRED",null,null,true);jdbc.update("UPDATE operator_vouchers SET starts_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 2 DAY),ends_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 DAY) WHERE id=?",expired);
        long draft=offer(admin,"DRAFT",null,null,true);jdbc.update("UPDATE operator_vouchers SET state='DRAFT' WHERE id=?",draft);
        offer(admin,"PRIVATE",null,null,false);assertThat(list()).extracting(PublicPromotionDtos.Promotion::code).containsExactly("PUBLIC25");
    }
    @Test void publicVisibilityAndCapacityDoNotChangeSearchPriceFiltersOrPagination() throws Exception {
        var pickup=f.stops().get(0).getPlannedDepartureTime();String day=pickup.atOffset(ZoneOffset.UTC).atZoneSameInstant(com.busgo.common.time.BusGoTime.BUSINESS_ZONE).toLocalDate().toString();
        var request=get("/api/v1/trips/search").param("pickupLocationId",f.locations().get(0).getId()+"").param("dropoffLocationId",f.locations().get(2).getId()+"").param("operatorId",f.operator().getId()+"").param("departureDate",day).param("sort","PRICE_ASC").param("size","1");
        var before=json.readTree(mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("data[0].hasPublicPromotions").value(false)).andReturn().getResponse().getContentAsString());publish();
        var after=json.readTree(mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("data[0].hasPublicPromotions").value(true)).andReturn().getResponse().getContentAsString());
        assertThat(after.path("data").get(0).path("price")).isEqualTo(before.path("data").get(0).path("price"));assertThat(after.path("pagination")).isEqualTo(before.path("pagination"));
        authenticate(admin);vouchers.visibility(admin,voucher,false);mvc.perform(request).andExpect(jsonPath("data[0].hasPublicPromotions").value(false));
    }
    @Test void globalQuotaChangingBetweenPublicViewAndBookingIsAuthoritative() {
        // A second campaign with no minimum enables a one-use real booking race scenario.
        authenticate(admin);long last=((Number)vouchers.create(admin,new Terms("LAST","Last public use",null,"FIXED_AMOUNT",BigDecimal.TEN,null,BigDecimal.ZERO,OffsetDateTime.now().minusDays(1),OffsetDateTime.now().plusDays(1),1,1,null)).get("id")).longValue();vouchers.state(admin,last,true);vouchers.visibility(admin,last,true);
        var other=customer("m304b-other");authenticate(owner.user());var ah=hold(owner,f,0,2,0);var bh=hold(other,f,0,2,1);
        assertThat(list()).extracting(PublicPromotionDtos.Promotion::code).contains("LAST");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_usages WHERE voucher_id=?",Long.class,last)).isZero();
        bookingService.create(owner.user(),new BookingDtos.CreateBookingRequest(ah.holdToken(),"C","0901234567",null,"LAST"));assertThat(list()).isEmpty();
        authenticate(other.user());assertThatThrownBy(()->bookingService.create(other.user(),new BookingDtos.CreateBookingRequest(bh.holdToken(),"C","0901234567",null,"LAST"))).isInstanceOf(BusinessException.class).extracting(e->((BusinessException)e).getCode()).isEqualTo("VOUCHER_LIMIT_REACHED");
        jdbc.update("UPDATE voucher_usages SET expires_at=? WHERE voucher_id=?",com.busgo.common.time.JpaJdbcTime.parameter(com.busgo.common.time.BusGoTime.utc(Instant.now().minusSeconds(60))),last);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voucher_usages WHERE voucher_id=? AND expires_at>?",Long.class,last,com.busgo.common.time.JpaJdbcTime.parameter(com.busgo.common.time.BusGoTime.utc(Instant.now())))).isZero();
        assertThat(list()).extracting(PublicPromotionDtos.Promotion::code).contains("LAST");
    }
    @Test void inactiveOperatorOrAssociationIsNotAdvertised() {
        publish();assertThat(list()).hasSize(1);f.operator().setStatus(com.busgo.operator.entity.OperatorStatus.SUSPENDED);em.flush();assertThat(list()).isEmpty();
        f.operator().setStatus(com.busgo.operator.entity.OperatorStatus.ACTIVE);f.operatorRoute().setStatus(com.busgo.common.entity.ActiveStatus.INACTIVE);em.flush();assertThat(list()).isEmpty();
    }
    @Test void pagesValidationAndNoAnonymousMutation() throws Exception {
        publish();offer(admin,"SECOND",null,null,true);var first=publicPromotions.list(f.operator().getId(),0,1);var second=publicPromotions.list(f.operator().getId(),1,1);
        assertThat(first.pagination().totalPages()).isEqualTo(2);assertThat(first.data().get(0).id()).isNotEqualTo(second.data().get(0).id());
        mvc.perform(get("/api/v1/public/promotions?size=1000")).andExpect(status().isBadRequest());mvc.perform(get("/api/v1/public/promotions?page=-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/trips/"+f.trip().getId()+"/promotions")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/public/promotions").header("Authorization",bearer(owner.user())).contentType("application/json").content("{}")).andExpect(status().isForbidden());
    }
}
