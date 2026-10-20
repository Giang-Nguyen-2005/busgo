package com.busgo;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.busgo.support.*;
import com.busgo.support.SupportDtos.*;
import com.busgo.common.security.CurrentUser;
import com.busgo.common.exception.BusinessException;
import com.busgo.user.entity.RoleCode;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties="busgo.notifications.initial-delay=PT24H")
@AutoConfigureMockMvc @ActiveProfiles("dev") @Transactional
class M28SupportIT extends M20Support {
    @Autowired SupportService support;
    @Autowired MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    Fixture f;UserAuth owner;long booking;CurrentUser admin,employee,system;
    @BeforeEach void prepare() {
        f=fixture();owner=customer("m28");booking=web(f,owner,0).bookingId();
        admin=actor(f,RoleCode.OPERATOR_ADMIN);employee=actor(f,RoleCode.OPERATOR_STAFF);system=actor(f,RoleCode.SYSTEM_ADMIN);
    }
    @AfterEach void clearSecurity() {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    Ticket create(Category category) {return support.create(owner.user(),new Create(booking,category,"Cần hỗ trợ","Nội dung hỗ trợ",UUID.randomUUID().toString()));}
    Command command(Ticket t,String content) {return new Command(t.version(),UUID.randomUUID().toString(),content,false,null,null,null,null);}
    Ticket act(CurrentUser u,String area,Ticket t,String action) {authenticate(u);return support.command(u,area,t.id(),action,command(t,"Nội dung xử lý"));}
    @Test void creationDerivesOwnerAndRetryDoesNotDuplicate() {
        var c=new Create(booking,Category.BAGGAGE,"Hành lý","Nội dung","create-retry");var a=support.create(owner.user(),c);var b=support.create(owner.user(),c);
        assertThat(b.id()).isEqualTo(a.id());assertThat(a.operatorId()).isEqualTo(f.operator().getId());assertThat(a.customerId()).isEqualTo(owner.user().id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE resource_type='SUPPORT' AND resource_id=?",Long.class,a.id())).isOne();
        assertThatThrownBy(()->support.create(owner.user(),new Create(booking,Category.BAGGAGE,"Khác","Nội dung","create-retry"))).isInstanceOf(BusinessException.class);
    }
    @Test void customerCannotForgeBookingOrBrowseForeignCase() {
        var t=create(Category.BAGGAGE);var other=customer("foreign");
        assertThatThrownBy(()->support.detail(other.user(),"customer",t.id())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->support.create(other.user(),new Create(booking,Category.BAGGAGE,"Giả","Nội dung","forged"))).isInstanceOf(BusinessException.class);
        assertThat(support.list(other.user(),"customer",null,null,0,20).data()).isEmpty();
    }
    @Test void tenantAndSystemPrivateCaseIsolation() {
        var t=create(Category.BAGGAGE);var foreign=actor(fixture(),RoleCode.OPERATOR_ADMIN);authenticate(foreign);
        assertThatThrownBy(()->support.detail(foreign,"operator",t.id())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->support.dispute(system,t.id())).isInstanceOf(BusinessException.class);
        authenticate(admin);assertThat(support.detail(admin,"operator",t.id()).id()).isEqualTo(t.id());
    }
    @Test void ordinaryLifecycleClaimResolveReopenAccept() {
        var t=create(Category.BAGGAGE);t=act(employee,"operator",t,"claim");assertThat(t.assignedUserId()).isEqualTo(employee.id());
        t=act(employee,"operator",t,"resolve");assertThat(t.status()).isEqualTo(State.RESOLVED);
        t=act(owner.user(),"customer",t,"reopen");assertThat(t.reopenCount()).isOne();
        t=act(employee,"operator",t,"resolve");t=act(owner.user(),"customer",t,"accept-resolution");assertThat(t.status()).isEqualTo(State.CLOSED);
        var closed=t;assertThatThrownBy(()->act(owner.user(),"customer",closed,"messages")).isInstanceOf(BusinessException.class);
    }
    @ParameterizedTest @EnumSource(Category.class)
    void categoryPolicyCannotBeEvaded(Category category) {
        var t=create(category);authenticate(employee);
        if(category.sensitive()) {
            assertThat(t.escalation()).isEqualTo(Escalation.OPERATOR_ADMIN);
            assertThatThrownBy(()->act(employee,"operator",t,"claim")).isInstanceOf(BusinessException.class);
            assertThatThrownBy(()->act(employee,"operator",t,"resolve")).isInstanceOf(BusinessException.class);
            assertThat(act(employee,"operator",t,"messages").status()).isEqualTo(State.OPEN);
        }else assertThat(act(employee,"operator",t,"claim").assignedUserId()).isEqualTo(employee.id());
    }
    @Test void notesHiddenFromCustomerAndImmutableTimeline() {
        var t=create(Category.BAGGAGE);authenticate(admin);
        support.command(admin,"operator",t.id(),"messages",new Command(t.version(),"note","Private note",true,null,null,null,null));
        assertThat(support.messages(owner.user(),"customer",t.id(),0,100).data()).noneMatch(a->a.content().contains("Private"));
        assertThat(support.messages(admin,"operator",t.id(),0,100).data()).anyMatch(a->a.content().equals("Private note"));
        assertThat(support.inbox(owner.user(),"customer",0,20).data()).isEmpty();
    }
    @Test void assignmentValidatesMembershipAndAuthority() {
        var t=create(Category.BAGGAGE);authenticate(admin);
        var assigned=support.command(admin,"operator",t.id(),"assign",new Command(t.version(),"assign","Phân công",false,employee.id(),null,null,null));
        assertThat(assigned.assignedUserId()).isEqualTo(employee.id());
        assertThat(support.inbox(employee,"operator",0,20).data()).hasSize(1);
        var foreign=actor(fixture(),RoleCode.OPERATOR_STAFF);authenticate(admin);
        assertThatThrownBy(()->support.command(admin,"operator",t.id(),"assign",new Command(assigned.version(),"foreign","Sai nhà xe",false,foreign.id(),null,null,null))).isInstanceOf(BusinessException.class);
    }
    @Test void removedStaffCannotReadAndDoesNotBlockAdminResolution() {
        var t=act(employee,"operator",create(Category.BAGGAGE),"claim");
        jdbc.update("UPDATE operator_staff SET status='INACTIVE' WHERE user_id=?",employee.id());em.clear();authenticate(employee);
        assertThatThrownBy(()->support.detail(employee,"operator",t.id())).isInstanceOf(BusinessException.class);
        assertThat(act(admin,"operator",t,"resolve").status()).isEqualTo(State.RESOLVED);
    }
    @Test void suspendedOperatorContinuesExistingPassengerSupport() {
        jdbc.update("UPDATE transport_operators SET status='SUSPENDED' WHERE id=?",f.operator().getId());em.clear();
        var t=create(Category.BAGGAGE);assertThat(act(employee,"operator",t,"claim").status()).isEqualTo(State.IN_PROGRESS);
    }
    @Test void staleMutationAndReplayAreProtected() {
        var t=create(Category.BAGGAGE);authenticate(employee);var c=command(t,"Tiếp nhận");var first=support.command(employee,"operator",t.id(),"claim",c);
        assertThat(support.command(employee,"operator",t.id(),"claim",c).version()).isEqualTo(first.version());
        assertThatThrownBy(()->support.command(employee,"operator",t.id(),"resolve",command(t,"Cũ"))).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE resource_id=? AND resource_type='SUPPORT'",Long.class,t.id())).isEqualTo(2);
    }
    @Test void customerAppealRequiresOperatorReviewBeforePlatform() {
        var t=create(Category.BAGGAGE);var initial=t;
        assertThatThrownBy(()->act(owner.user(),"customer",initial,"appeal")).isInstanceOf(BusinessException.class);
        t=act(employee,"operator",t,"claim");t=act(employee,"operator",t,"resolve");t=act(owner.user(),"customer",t,"appeal");
        assertThat(t.escalation()).isEqualTo(Escalation.OPERATOR_ADMIN);
        assertThatThrownBy(()->support.dispute(system,initial.id())).isInstanceOf(BusinessException.class);
        t=act(admin,"operator",t,"resolve");t=act(owner.user(),"customer",t,"appeal");assertThat(t.escalation()).isEqualTo(Escalation.PLATFORM);
        assertThat(support.dispute(system,t.id()).status()).isEqualTo("SUBMITTED");
    }
    @Test void platformDecisionPreservesCommerceAndRecordsHistory() {
        var before=commerce();var t=create(Category.REFUND_COMPLAINT);t=act(admin,"operator",t,"escalate");
        assertThat(support.inbox(system,"admin",0,20).data()).hasSize(1);
        t=act(system,"admin",t,"review");t=act(system,"admin",t,"request-information");t=act(owner.user(),"customer",t,"messages");t=act(system,"admin",t,"review");
        var c=new Command(t.version(),"decision","Nhà xe cần xem xét theo quy trình hủy vé hiện có",false,null,null,null,Outcome.CUSTOMER_COMPLAINT_UPHELD);
        t=support.command(system,"admin",t.id(),"decision",c);assertThat(t.status()).isEqualTo(State.CLOSED);
        assertThat(support.dispute(system,t.id()).outcome()).isEqualTo(Outcome.CUSTOMER_COMPLAINT_UPHELD);
        assertThat(commerce()).isEqualTo(before);
        assertThat(support.messages(owner.user(),"customer",t.id(),0,100).data()).anyMatch(a->a.eventType().startsWith("DECISION_"));
    }
    private String commerce() {return List.of("bookings","booking_items","payments","tickets","refunds","trip_seat_segment_inventory","voucher_usages").stream().map(table->jdbc.queryForList("SELECT * FROM "+table+" ORDER BY id").toString()).reduce("",String::concat);}
    @Test void reopenWindowAndLimitAreBounded() {
        var t=act(admin,"operator",create(Category.BAGGAGE),"resolve");jdbc.update("UPDATE support_tickets SET resolved_at=UTC_TIMESTAMP()-INTERVAL 8 DAY WHERE id=?",t.id());
        var expired=t;assertThatThrownBy(()->act(owner.user(),"customer",expired,"reopen")).isInstanceOf(BusinessException.class);
        jdbc.update("UPDATE support_tickets SET resolved_at=UTC_TIMESTAMP(),reopen_count=2 WHERE id=?",t.id());
        assertThatThrownBy(()->act(owner.user(),"customer",expired,"reopen")).isInstanceOf(BusinessException.class);
    }
    @Test void auditIsTenantScopedRedactedAndStaffDenied() {
        var t=create(Category.BAGGAGE);authenticate(admin);
        assertThat(support.audits(admin,"operator","SUPPORT",t.id(),0,20).data()).hasSize(1);
        var foreign=actor(fixture(),RoleCode.OPERATOR_ADMIN);authenticate(foreign);assertThat(support.audits(foreign,"operator","SUPPORT",t.id(),0,20).data()).isEmpty();
        authenticate(employee);assertThatThrownBy(()->support.audits(employee,"operator",null,null,0,20)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT metadata FROM audit_events WHERE resource_type='SUPPORT' AND resource_id=?",String.class,t.id())).isEqualTo("{}");
    }
    @Test void httpGuardsValidationAndStaffWriteScope() throws Exception {
        var t=create(Category.BAGGAGE);
        mvc.perform(get("/api/v1/support/tickets")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/operator/audit-events").header("Authorization",bearer(employee))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/disputes/tickets/"+t.id()).header("Authorization",bearer(system))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/operator/bookings").header("Authorization",bearer(employee)).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/support/tickets").header("Authorization","Bearer "+owner.token()).contentType("application/json").content("{\"bookingId\":1,\"category\":\"BAGGAGE\",\"subject\":\"\",\"description\":\"\",\"requestKey\":\"x\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/support/tickets/"+t.id()+"/messages?size=101").header("Authorization","Bearer "+owner.token())).andExpect(status().isBadRequest());
    }
    @Test void dashboardIncludesZeroStatesAndTenantAssignmentScopes() {
        create(Category.BAGGAGE);authenticate(admin);
        var counters=support.dashboard(admin,"operator");
        assertThat(counters).contains(new Counter("OPEN",1),new Counter("RESOLVED",0),new Counter("UNASSIGNED",1));
        authenticate(employee);assertThat(support.dashboard(employee,"operator")).contains(new Counter("OPEN",0),new Counter("AVAILABLE",1));
        var foreign=actor(fixture(),RoleCode.OPERATOR_ADMIN);authenticate(foreign);
        assertThat(support.dashboard(foreign,"operator")).contains(new Counter("OPEN",0),new Counter("UNASSIGNED",0));
        assertThat(support.dashboard(system,"admin")).extracting(Counter::key).containsExactly("SUBMITTED","UNDER_REVIEW","WAITING_INFORMATION","CLOSED");
    }
}
