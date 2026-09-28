package com.busgo;

import static org.assertj.core.api.Assertions.assertThat;
import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.security.*;
import com.busgo.operator.entity.*;
import com.busgo.operator.repository.*;
import com.busgo.user.entity.*;
import com.busgo.user.repository.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev")
class M13StaffConcurrencyIT extends JwtTestSupport {
    @Autowired MockMvc mvc; @Autowired JwtService jwt; @Autowired UserRepository users;
    @Autowired RoleRepository roles; @Autowired UserRoleRepository userRoles;
    @Autowired TransportOperatorRepository operators; @Autowired OperatorStaffRepository staff;

    @Test void concurrentDemotionsCannotRemoveEveryActiveAdmin() throws Exception {
        TransportOperator operator=new TransportOperator();operator.setName("M13 race "+UUID.randomUUID());
        operator.setCode(UUID.randomUUID().toString());operator.setStatus(OperatorStatus.ACTIVE);operators.saveAndFlush(operator);
        Admin a=admin(operator);Admin b=admin(operator);
        CyclicBarrier barrier=new CyclicBarrier(2);ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first=pool.submit(()->demote(barrier,a.token(),b.staffId()));
            Future<Integer> second=pool.submit(()->demote(barrier,b.token(),a.staffId()));
            List<Integer> statuses=List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
            assertThat(staff.countActiveLoginCapableAdmins(operator.getId())).isEqualTo(1);
            assertThat(statuses).contains(200).anyMatch(value->value==409||value==403);
        } finally { pool.shutdownNow(); }
    }
    private int demote(CyclicBarrier barrier,String token,Long id)throws Exception {
        barrier.await(10,TimeUnit.SECONDS);
        return mvc.perform(patch("/api/v1/operator/staff/{id}",id).header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"OPERATOR_STAFF\"}"))
                .andReturn().getResponse().getStatus();
    }
    private Admin admin(TransportOperator operator){
        User u=new User();u.setFullName("Race admin");u.setEmail(UUID.randomUUID()+"@example.test");
        u.setPhone("0900000013");u.setPasswordHash("unused");u.setStatus(UserStatus.ACTIVE);users.saveAndFlush(u);
        userRoles.saveAndFlush(new UserRole(u,roles.findByCode(RoleCode.OPERATOR_ADMIN).orElseThrow()));
        OperatorStaff s=new OperatorStaff();s.setOperator(operator);s.setUser(u);s.setStaffCode(UUID.randomUUID().toString());
        s.setStatus(ActiveStatus.ACTIVE);staff.saveAndFlush(s);
        return new Admin(s.getId(),jwt.issue(new CurrentUser(u.getId(),List.of(RoleCode.OPERATOR_ADMIN)),"access"));
    }
    private record Admin(Long staffId,String token){}
}
