package com.busgo;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.busgo.onboarding.*;
import com.busgo.onboarding.OnboardingDtos.*;
import com.busgo.common.security.CurrentUser;
import com.busgo.common.exception.BusinessException;
import com.busgo.user.entity.RoleCode;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties="busgo.notifications.initial-delay=PT24H")
@AutoConfigureMockMvc @ActiveProfiles("dev") @Transactional
class M26OnboardingIT extends M26Support {
    @Autowired MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired com.busgo.marketplace.MarketplaceService marketplace;
    @Autowired com.busgo.admin.AdminOperatorService directory;
    @Autowired com.busgo.operator.OperatorContextService context;
    @Test void publicSubmitCreatesOnlyApplicationAndSafeReceipt() throws Exception {
        var r=request();long users=count("users"),ops=count("transport_operators");
        mvc.perform(post("/api/v1/public/operator-applications").contentType("application/json").content(json.writeValueAsBytes(r)))
            .andExpect(status().isCreated()).andExpect(jsonPath("data.status").value("SUBMITTED"));
        assertThat(count("users")).isEqualTo(users);assertThat(count("transport_operators")).isEqualTo(ops);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE event_type='APPLICATION_RECEIVED' AND booking_id IS NULL",Long.class)).isPositive();
    }
    @ParameterizedTest @ValueSource(strings={"name","legalCode","email","phone","address","contactName","adminEmail"})
    void invalidFieldsRejected(String field) throws Exception {
        var body=json.convertValue(request(),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});body.put(field,"");
        mvc.perform(post("/api/v1/public/operator-applications").contentType("application/json").content(json.writeValueAsBytes(body))).andExpect(status().isBadRequest());
    }
    @Test void duplicateNormalizedOpenIdentityDenied() {
        var r=request();submit(r);assertThatThrownBy(()->submit(r)).isInstanceOf(BusinessException.class).hasMessageContaining("Mã đăng ký");
    }
    @Test void rejectionRequiresReasonAndCreatesNoOperator() {
        var u=system();long id=submit(request()),before=count("transport_operators");
        assertThatThrownBy(()->onboarding.reject(u,id,new Reason(" "))).isInstanceOf(BusinessException.class);
        onboarding.reject(u,id,new Reason("Thông tin cần bổ sung"));assertThat(count("transport_operators")).isEqualTo(before);
        assertThat(onboarding.detail(u,id).get("status")).isEqualTo("REJECTED");
    }
    @Test void rejectedIdentityCanSubmitNewImmutableApplication() {
        var u=system();var r=request();long id=submit(r);onboarding.reject(u,id,new Reason("Cần bổ sung"));long next=submit(r);
        assertThat(next).isNotEqualTo(id);assertThat(onboarding.detail(u,id).get("status")).isEqualTo("REJECTED");
        assertThatThrownBy(()->onboarding.review(u,id,new Decision(null))).isInstanceOf(BusinessException.class);
    }
    @Test void reviewRequiredBeforeApproval() {
        var u=system();long id=submit(request());assertThatThrownBy(()->onboarding.approve(u,id,new Decision(null))).isInstanceOf(BusinessException.class);
        onboarding.review(u,id,new Decision("Internal only"));assertThat(onboarding.detail(u,id).get("status")).isEqualTo("UNDER_REVIEW");
    }
    @Test void approvalCreatesInactiveHashOnlyAndNoUserOrSupply() {
        var u=system();var r=request();long id=submit(r),users=count("users"),trips=count("trips");var invite=approve(u,id);
        assertThat(count("users")).isEqualTo(users);assertThat(count("trips")).isEqualTo(trips);
        assertThat(jdbc.queryForObject("SELECT status FROM transport_operators WHERE id=?",String.class,invite.operatorId())).isEqualTo("INACTIVE");
        String raw=raw(invite);assertThat(jdbc.queryForObject("SELECT token_hash FROM operator_admin_invitations WHERE application_id=?",String.class,id)).isEqualTo(com.busgo.auth.AuthService.hash(raw)).doesNotContain(raw);
        assertThat(onboarding.detail(u,id).toString()).doesNotContain(raw,"token_hash","invitationPath");
        assertThat(onboarding.governance(u,invite.operatorId()).toString()).doesNotContain(raw,"token_hash");
        assertThat(jdbc.queryForList("SELECT * FROM notifications WHERE recipient_key=?","application:"+id).toString()).doesNotContain(raw);
        assertThat(jdbc.queryForList("SELECT * FROM operator_onboarding_history WHERE application_id=?",id).toString()).doesNotContain(raw);
        assertThatThrownBy(()->marketplace.profile(invite.operatorId())).isInstanceOf(com.busgo.common.exception.BusinessException.class);
        assertThatThrownBy(()->submit(r)).isInstanceOf(BusinessException.class);
    }
    @Test void retryApprovalAndTerminalRejectionCannotDuplicate() {
        var u=system();long id=submit(request());approve(u,id);long count=count("transport_operators");
        assertThatThrownBy(()->onboarding.approve(u,id,new Decision(null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->onboarding.reject(u,id,new Reason("No"))).isInstanceOf(BusinessException.class);
        assertThat(count("transport_operators")).isEqualTo(count);
    }
    @Test void acceptanceCreatesDedicatedAdminActivatesAndNormalLoginWorks() {
        var u=system();var r=request();var i=approve(u,submit(r));assertThat(onboarding.context(raw(i)).get("email")).isEqualTo(r.adminEmail());
        onboarding.accept(raw(i),acceptance());
        var login=auth.login(new com.busgo.auth.AuthDtos.LoginRequest(r.adminEmail(),"test password"));
        assertThat(login.user().roles()).containsExactly(RoleCode.OPERATOR_ADMIN);
        var a=new CurrentUser(login.user().id(),login.user().roles());authenticate(a);
        assertThat(context.requireAdminOperator(a).getId()).isEqualTo(i.operatorId());
        assertThat(jdbc.queryForObject("SELECT status FROM transport_operators WHERE id=?",String.class,i.operatorId())).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operator_staff WHERE operator_id=? AND status='ACTIVE'",Long.class,i.operatorId())).isOne();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE email=?",String.class,r.adminEmail())).startsWith("$2").doesNotContain("test password");
        assertThat(marketplace.profile(i.operatorId()).operator().id()).isEqualTo(i.operatorId());
        assertThatThrownBy(()->onboarding.accept(raw(i),acceptance())).isInstanceOf(BusinessException.class);
    }
    @Test void expiredTokenDenied() {
        var u=system();var i=approve(u,submit(request()));jdbc.update("UPDATE operator_admin_invitations SET created_at=UTC_TIMESTAMP()-INTERVAL 4 DAY,expires_at=UTC_TIMESTAMP()-INTERVAL 1 DAY WHERE application_id=?",i.applicationId());
        assertThatThrownBy(()->onboarding.accept(raw(i),acceptance())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->onboarding.context(raw(i))).isInstanceOf(BusinessException.class);
    }
    @Test void reissueRevokesOldAndNewTokenWorks() {
        var u=system();var i=approve(u,submit(request()));var next=onboarding.reissue(u,i.applicationId());
        assertThatThrownBy(()->onboarding.accept(raw(i),acceptance())).isInstanceOf(BusinessException.class);
        onboarding.accept(raw(next),acceptance());assertThatThrownBy(()->onboarding.reissue(u,i.applicationId())).isInstanceOf(BusinessException.class);
    }
    @Test void malformedTokenGenericError() { assertThatThrownBy(()->onboarding.context("bad-token")).isInstanceOf(BusinessException.class).hasMessageContaining("không hợp lệ"); }
    @Test void confirmationAndPasswordPolicyEnforced() {
        var i=approve(system(),submit(request()));
        assertThatThrownBy(()->onboarding.accept(raw(i),new Acceptance("Admin","short","short"))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->onboarding.accept(raw(i),new Acceptance("Admin","test password","different"))).isInstanceOf(BusinessException.class);
    }
    @ParameterizedTest @EnumSource(RoleCode.class)
    void existingIdentityNeverReceivesAdminRole(RoleCode role) {
        var u=system();var r=request();long id=submit(r);onboarding.review(u,id,new Decision(null));account(r.adminEmail(),role);
        assertThatThrownBy(()->onboarding.approve(u,id,new Decision(null))).isInstanceOf(BusinessException.class).hasMessageContaining("email riêng");
    }
    @Test void emailTakenAfterApprovalRollsBackAcceptance() {
        var r=request();var i=approve(system(),submit(r));account(r.adminEmail(),RoleCode.CUSTOMER);
        assertThatThrownBy(()->onboarding.accept(raw(i),acceptance())).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT accepted_at FROM operator_admin_invitations WHERE application_id=?",Object.class,i.applicationId())).isNull();
    }
    @ParameterizedTest @EnumSource(value=RoleCode.class,names={"CUSTOMER","OPERATOR_ADMIN","OPERATOR_STAFF"})
    void platformApisDenyOtherRoles(RoleCode role) throws Exception {
        var u=account(UUID.randomUUID()+"@example.test",role);String token=bearer(u);
        for(String path:List.of("/admin/operator-applications","/admin/dashboard","/admin/operators"))
            mvc.perform(get("/api/v1"+path).header("Authorization",token)).andExpect(status().isForbidden());
        for(String path:List.of("/admin/operator-applications/1/review","/admin/operator-applications/1/approve","/admin/operator-applications/1/reject","/admin/operator-applications/1/invitation/reissue","/admin/operators/1/suspend","/admin/operators/1/reactivate"))
            mvc.perform(post("/api/v1"+path).header("Authorization",token).contentType("application/json").content("{\"reason\":\"test\"}")).andExpect(status().isForbidden());
    }
    @Test void noPublicListsAndNoSystemImpersonation() throws Exception {
        mvc.perform(get("/api/v1/public/operator-applications")).andExpect(status().isUnauthorized());
        var u=system();for(String path:List.of("/operator/trips","/operator/bookings","/bookings"))
            mvc.perform(get("/api/v1"+path).header("Authorization",bearer(u))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/operator-applications").header("Authorization",bearer(u))).andExpect(status().isOk());
    }
    @Test void dashboardDirectoryAndHistory() throws Exception {
        var u=system();long before=onboarding.dashboard(u).get("ONBOARDING");var i=approve(u,submit(request()));
        assertThat(onboarding.dashboard(u).get("ONBOARDING")).isEqualTo(before+1);
        assertThat(directory.list("ONB-"+i.applicationId(),null,0,20).data()).hasSize(1);
        mvc.perform(delete("/api/v1/admin/operator-applications/"+i.applicationId()).header("Authorization",bearer(u))).andExpect(status().isMethodNotAllowed());
        mvc.perform(patch("/api/v1/admin/operators/"+i.operatorId()+"/status").header("Authorization",bearer(u)).contentType("application/json").content("{\"status\":\"ACTIVE\"}")).andExpect(status().isConflict());
        onboarding.accept(raw(i),acceptance());onboarding.status(u,i.operatorId(),new Reason("Review"),true);onboarding.status(u,i.operatorId(),new Reason("Resolved"),false);
        assertThat(jdbc.queryForList("SELECT event_type FROM operator_onboarding_history WHERE operator_id=? ORDER BY id",String.class,i.operatorId())).containsExactly("APPLICATION_APPROVED","ADMIN_INVITE_CREATED","ADMIN_INVITE_ACCEPTED","OPERATOR_ACTIVATED","OPERATOR_SUSPENDED","OPERATOR_REACTIVATED");
    }
    @Test void reactivationNeedsActiveDedicatedAdmin() {
        var u=system();var i=approve(u,submit(request()));onboarding.accept(raw(i),acceptance());onboarding.status(u,i.operatorId(),new Reason("Review"),true);
        jdbc.update("UPDATE operator_staff SET status='INACTIVE' WHERE operator_id=?",i.operatorId());
        assertThatThrownBy(()->onboarding.status(u,i.operatorId(),new Reason("Resolved"),false)).isInstanceOf(BusinessException.class);
    }
}

abstract class M26Support extends M20Support {
    @Autowired OnboardingService onboarding;
    ApplicationRequest request() { String id=UUID.randomUUID().toString();return new ApplicationRequest("M26 "+id,"Legal Company","TAX-"+id,"business@example.test","0901234567","Hanoi","Representative",id+"@example.test","https://example.test","Operator description"); }
    long submit(ApplicationRequest r) { return ((Number)onboarding.submit(r).get("id")).longValue(); }
    InviteResult approve(CurrentUser u,long id) { onboarding.review(u,id,new Decision("Internal review"));return onboarding.approve(u,id,new Decision("Approved")); }
    String raw(InviteResult i) { return i.invitationPath().split("#")[1]; }
    Acceptance acceptance() { return new Acceptance("Operator Admin","test password","test password"); }
    CurrentUser system() { return account(UUID.randomUUID()+"@example.test",RoleCode.SYSTEM_ADMIN); }
    CurrentUser account(String email,RoleCode role) {
        jdbc.update("INSERT INTO users(full_name,email,phone,password_hash,status,created_at,updated_at) VALUES('M26',?,'0901234567','unused','ACTIVE',UTC_TIMESTAMP(),UTC_TIMESTAMP())",email);
        long id=jdbc.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code=?",id,role.name());return new CurrentUser(id,List.of(role));
    }
    long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class); }
    @AfterEach void clearSecurity() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
}
