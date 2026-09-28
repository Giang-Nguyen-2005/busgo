package com.busgo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.busgo.admin.bootstrap.*;
import com.busgo.auth.AuthDtos.LoginRequest;
import com.busgo.auth.AuthService;
import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.exception.BusinessException;
import com.busgo.common.security.CurrentUser;
import com.busgo.operator.entity.*;
import com.busgo.operator.repository.OperatorStaffRepository;
import com.busgo.user.entity.*;
import com.busgo.user.repository.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev") @Transactional
class M13SystemAdminIT extends M8BookingTestSupport {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired AuthService auth;
    @Autowired UserRepository users; @Autowired RoleRepository roles; @Autowired UserRoleRepository userRoles;
    @Autowired OperatorStaffRepository staff; @Autowired SystemAdminBootstrapService bootstrap;
    @Autowired SystemAdminBootstrapProperties bootstrapProperties;

    @Test void bootstrapDefaultsOffIsIdempotentAndUsesNormalLogin() {
        org.assertj.core.api.Assertions.assertThat(bootstrapProperties.enabled()).isFalse();
        String marker=UUID.randomUUID().toString();
        var p=new SystemAdminBootstrapProperties(true,"M13 Root",marker+"@example.test","0900000013","test password");
        org.assertj.core.api.Assertions.assertThat(bootstrap.provision(p)).isTrue();
        org.assertj.core.api.Assertions.assertThat(bootstrap.provision(p)).isFalse();
        var login=auth.login(new LoginRequest(p.email(),p.password()));
        org.assertj.core.api.Assertions.assertThat(login.user().roles()).containsExactly(RoleCode.SYSTEM_ADMIN);
        var wrong=new SystemAdminBootstrapProperties(true,"Other",p.email(),p.phone(),p.password());
        org.assertj.core.api.Assertions.assertThatThrownBy(()->bootstrap.provision(wrong))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void systemAdminManagesOperatorsButCannotEnterOperatorScope() throws Exception {
        String system=token(account(RoleCode.SYSTEM_ADMIN,null));
        String customer=token(account(RoleCode.CUSTOMER,null));
        mvc.perform(get("/api/v1/admin/operators").header("Authorization",bearer(customer)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/operator/trips").header("Authorization",bearer(system)))
                .andExpect(status().isForbidden());
        String code="M13-"+UUID.randomUUID().toString().substring(0,8);
        String email=UUID.randomUUID()+"@example.test";
        JsonNode created=data(postJson("/api/v1/admin/operators",system,Map.of(
                "name","M13 Operator","code",code.toLowerCase(Locale.ROOT),"status","ACTIVE",
                "phone","0901111111","email","ops@example.test","address","Test address",
                "initialAdmin",Map.of("fullName","Initial Admin","email",email,"phone","0902222222",
                        "password","test password","staffCode","root-1")))
                .andExpect(status().isCreated()).andExpect(jsonPath("data.code").value(code.toUpperCase(Locale.ROOT)))
                .andExpect(jsonPath("data.staffCounts.active").value(1)));
        long id=created.path("id").asLong();
        mvc.perform(get("/api/v1/admin/operators").header("Authorization",bearer(system))
                        .param("q",code).param("status","ACTIVE").param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("data[0].id").value(id));
        patchJson("/api/v1/admin/operators/"+id,system,Map.of("name","Updated Operator"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.name").value("Updated Operator"));
        patchJson("/api/v1/admin/operators/"+id+"/status",system,Map.of("status","INACTIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.status").value("INACTIVE"));
        patchJson("/api/v1/admin/operators/"+id+"/status",system,Map.of("status","ACTIVE"))
                .andExpect(status().isOk());
        postJson("/api/v1/admin/operators",system,Map.of("name","Duplicate","code",code,"status","ACTIVE",
                "initialAdmin",Map.of("fullName","Other","email",UUID.randomUUID()+"@example.test",
                        "phone","0903333333","password","test password","staffCode","root-2")))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("OPERATOR_CODE_ALREADY_EXISTS"));

        TransportOperator orphan=new TransportOperator();orphan.setName("Orphan "+UUID.randomUUID());
        orphan.setCode(UUID.randomUUID().toString());orphan.setStatus(OperatorStatus.INACTIVE);operators.saveAndFlush(orphan);
        patchJson("/api/v1/admin/operators/"+orphan.getId()+"/status",system,Map.of("status","ACTIVE"))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("OPERATOR_ACTIVATION_NOT_ALLOWED"));
    }

    @Test void operatorAdminCreatesAndControlsDedicatedStaffSafely() throws Exception {
        Fixture f=fixture(); User admin=account(RoleCode.OPERATOR_ADMIN,f.operator()); String adminToken=token(admin);
        String email=UUID.randomUUID()+"@example.test";
        JsonNode created=data(postJson("/api/v1/operator/staff",adminToken,Map.of("fullName","Staff One",
                "email",email,"phone","0904444444","password","test password","staffCode","staff-1",
                "role","OPERATOR_STAFF")).andExpect(status().isCreated())
                .andExpect(jsonPath("data.role").value("OPERATOR_STAFF")));
        long staffId=created.path("staffId").asLong();
        mvc.perform(get("/api/v1/operator/staff").header("Authorization",bearer(adminToken))
                        .param("q","staff-1").param("role","OPERATOR_STAFF"))
                .andExpect(status().isOk()).andExpect(jsonPath("data[0].staffId").value(staffId));
        patchJson("/api/v1/operator/staff/"+staffId,adminToken,Map.of("role","OPERATOR_ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.role").value("OPERATOR_ADMIN"));
        patchJson("/api/v1/operator/staff/"+staffId,adminToken,Map.of("status","INACTIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.membershipStatus").value("INACTIVE"));
        postJson("/api/v1/operator/staff",adminToken,Map.of("fullName","Duplicate","email",UUID.randomUUID()+"@example.test",
                "phone","0905555555","password","test password","staffCode","staff-1","role","OPERATOR_STAFF"))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("STAFF_CODE_ALREADY_EXISTS"));
        long own=staff.findByUserId(admin.getId()).get(0).getId();
        patchJson("/api/v1/operator/staff/"+own,adminToken,Map.of("role","OPERATOR_STAFF"))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("LAST_OPERATOR_ADMIN_REQUIRED"));
        patchJson("/api/v1/operator/staff/999999999",adminToken,Map.of("status","INACTIVE"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("STAFF_NOT_FOUND"));

        Fixture foreign=fixture();User foreignMember=account(RoleCode.OPERATOR_STAFF,foreign.operator());
        long foreignId=staff.findByUserId(foreignMember.getId()).get(0).getId();
        patchJson("/api/v1/operator/staff/"+foreignId,adminToken,Map.of("status","INACTIVE"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("STAFF_NOT_FOUND"));

        User conflicted=account(RoleCode.OPERATOR_STAFF,foreign.operator());
        OperatorStaff local=new OperatorStaff();local.setOperator(f.operator());local.setUser(conflicted);
        local.setStaffCode(UUID.randomUUID().toString());local.setStatus(ActiveStatus.INACTIVE);staff.saveAndFlush(local);
        patchJson("/api/v1/operator/staff/"+local.getId(),adminToken,Map.of("status","ACTIVE"))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("STAFF_MEMBERSHIP_CONFLICT"));
    }

    @Test void operatorStaffHasReadOnlyOperationalAccess() throws Exception {
        Fixture f=fixture(); User staffUser=account(RoleCode.OPERATOR_STAFF,f.operator()); String token=token(staffUser);
        UserAuth customer=customer("m13-read");long booking=createBooking(customer,hold(customer,f,0,1,0).holdToken());
        mvc.perform(get("/api/v1/operator/trips").header("Authorization",bearer(token)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/operator/trips/{id}",f.trip().getId()).header("Authorization",bearer(token)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/operator/trips/{id}/passengers",f.trip().getId()).header("Authorization",bearer(token)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/operator/trips/{id}/occupancy",f.trip().getId()).header("Authorization",bearer(token)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/operator/bookings").header("Authorization",bearer(token))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/operator/bookings/{id}",booking).header("Authorization",bearer(token))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/operator/trips").header("Authorization",bearer(token)).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/operator/staff").header("Authorization",bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test void inactiveOperatorStopsNewCommerceButPreservesHistory() throws Exception {
        Fixture f=fixture(); UserAuth paidOwner=customer("m13-paid");
        long paid=createBooking(paidOwner,hold(paidOwner,f,0,1,0).holdToken()); confirm(paidOwner,paid).andExpect(status().isOk());
        UserAuth pendingOwner=customer("m13-pending"); long pending=createBooking(pendingOwner,hold(pendingOwner,f,1,2,0).holdToken());
        UserAuth heldOwner=customer("m13-held"); String held=hold(heldOwner,f,2,3,0).holdToken();
        f.operator().setStatus(OperatorStatus.INACTIVE); operators.saveAndFlush(f.operator());
        mvc.perform(get("/api/v1/trips/search").param("pickupLocationId",f.locations().get(0).getId().toString())
                        .param("dropoffLocationId",f.locations().get(1).getId().toString()).param("departureDate","2030-09-20"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.length()").value(0));
        mvc.perform(get("/api/v1/trips/{id}",f.trip().getId()).param("pickupLocationId",f.locations().get(0).getId().toString())
                        .param("dropoffLocationId",f.locations().get(1).getId().toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("TRIP_NOT_BOOKABLE"));
        mvc.perform(get("/api/v1/trips/{id}/seats",f.trip().getId()).param("pickupLocationId",f.locations().get(0).getId().toString())
                        .param("dropoffLocationId",f.locations().get(1).getId().toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("TRIP_NOT_BOOKABLE"));
        mvc.perform(post("/api/v1/seat-holds").header("Authorization",bearer(heldOwner.token())).contentType("application/json")
                        .content(json.writeValueAsBytes(Map.of("tripId",f.trip().getId(),
                                "pickupLocationId",f.locations().get(0).getId(),"dropoffLocationId",f.locations().get(1).getId(),
                                "tripSeatIds",List.of(f.seats().get(1).getId())))))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("TRIP_NOT_BOOKABLE"));
        confirm(pendingOwner,pending).andExpect(status().isConflict()).andExpect(jsonPath("code").value("PAYMENT_WINDOW_CLOSED"));
        mvc.perform(post("/api/v1/bookings").header("Authorization",bearer(heldOwner.token())).contentType("application/json").content(bookingBody(held)))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("TRIP_NOT_BOOKABLE"));
        mvc.perform(get("/api/v1/bookings/{id}",paid).header("Authorization",bearer(paidOwner.token())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/bookings/{id}/ticket",paid).header("Authorization",bearer(paidOwner.token())))
                .andExpect(status().isOk());
    }

    private User account(RoleCode role,TransportOperator operator){
        User u=new User();u.setFullName("M13 "+role);u.setEmail(UUID.randomUUID()+"@example.test");u.setPhone("09"+UUID.randomUUID().toString().replace("-","").substring(0,8));
        u.setPasswordHash("not-used");u.setStatus(UserStatus.ACTIVE);users.saveAndFlush(u);
        userRoles.saveAndFlush(new UserRole(u,roles.findByCode(role).orElseThrow()));
        if(operator!=null){OperatorStaff s=new OperatorStaff();s.setOperator(operator);s.setUser(u);s.setStaffCode(UUID.randomUUID().toString());s.setStatus(ActiveStatus.ACTIVE);staff.saveAndFlush(s);} return u;
    }
    private String token(User u){return jwt.issue(new CurrentUser(u.getId(),userRoles.findRoleCodesByUserId(u.getId())),"access");}
    private ResultActions postJson(String path,String token,Object body)throws Exception{return mvc.perform(post(path).header("Authorization",bearer(token)).contentType("application/json").content(json.writeValueAsBytes(body)));}
    private ResultActions patchJson(String path,String token,Object body)throws Exception{return mvc.perform(patch(path).header("Authorization",bearer(token)).contentType("application/json").content(json.writeValueAsBytes(body)));}
    private JsonNode data(ResultActions r)throws Exception{return json.readTree(r.andReturn().getResponse().getContentAsByteArray()).path("data");}
    private long createBooking(UserAuth owner,String hold)throws Exception{return ((Number)com.jayway.jsonpath.JsonPath.read(mvc.perform(post("/api/v1/bookings").header("Authorization",bearer(owner.token())).contentType("application/json").content(bookingBody(hold))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),"$.data.bookingId")).longValue();}
    private ResultActions confirm(UserAuth owner,long bookingId)throws Exception{return mvc.perform(post("/api/v1/bookings/{id}/payments/mock-confirm",bookingId).header("Authorization",bearer(owner.token())));}
    private static String bearer(String token){return "Bearer "+token;}
}
