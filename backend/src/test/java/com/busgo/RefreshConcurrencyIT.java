package com.busgo;

import com.busgo.auth.AuthDtos.*;
import com.busgo.auth.AuthService;
import com.busgo.common.security.*;
import com.busgo.user.entity.RoleCode;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

/** Separate committed transactions are necessary to test real MySQL row locking. */
@SpringBootTest
@ActiveProfiles("dev")
class RefreshConcurrencyIT extends JwtTestSupport {
    @Autowired AuthService service;
    @Autowired JdbcTemplate jdbc;
    @Test void concurrentRefreshRequestsHaveExactlyOneWinner() throws Exception {
        var registration=service.register(new RegisterRequest("Concurrent",UUID.randomUUID()+"@example.test","0901234567","test password"));
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            var login=service.login(new LoginRequest(registration.email(),"test password"));
            var barrier=new CyclicBarrier(2);
            Callable<Boolean> rotate=()-> {
                barrier.await(10,TimeUnit.SECONDS);
                try { service.refresh(new RefreshRequest(login.refreshToken())); return true; }
                catch (AuthenticationFailure ex) { assertThat(ex.getCode()).isEqualTo("REFRESH_TOKEN_INVALID"); return false; }
            };
            var first=executor.submit(rotate); var second=executor.submit(rotate);
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id=?",Long.class,registration.id())).isEqualTo(1);
        } finally { executor.shutdownNow(); cleanup(registration.id()); }
    }
    @Test void passwordChangeAndRefreshCannotLeaveAUsableRefreshToken() throws Exception {
        var registration=service.register(new RegisterRequest("Concurrent",UUID.randomUUID()+"@example.test","0901234567","test password"));
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            var login=service.login(new LoginRequest(registration.email(),"test password"));
            var barrier=new CyclicBarrier(2);
            var refresh=executor.submit(()-> {
                barrier.await(10,TimeUnit.SECONDS);
                try { return service.refresh(new RefreshRequest(login.refreshToken())); }
                catch (AuthenticationFailure ex) { assertThat(ex.getCode()).isEqualTo("REFRESH_TOKEN_INVALID"); return null; }
            });
            var change=executor.submit(()-> {
                barrier.await(10,TimeUnit.SECONDS);
                service.changePassword(new CurrentUser(registration.id(),List.of(RoleCode.CUSTOMER)),new ChangePasswordRequest("test password","changed password"));
                return true;
            });
            var result=refresh.get(20,TimeUnit.SECONDS); assertThat(change.get(20,TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id=?",Long.class,registration.id())).isZero();
            if (result!=null) assertThatThrownBy(()->service.refresh(new RefreshRequest(result.refreshToken()))).isInstanceOf(AuthenticationFailure.class);
        } finally { executor.shutdownNow(); cleanup(registration.id()); }
    }
    private void cleanup(Long userId) {
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id=?",userId);
        jdbc.update("DELETE FROM user_roles WHERE user_id=?",userId);
        jdbc.update("DELETE FROM users WHERE id=?",userId);
    }
}
