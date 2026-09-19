package com.busgo;

import com.busgo.auth.AuthService;
import com.busgo.auth.repository.RefreshTokenRepository;
import com.busgo.common.security.*;
import com.busgo.user.entity.*;
import com.busgo.user.repository.*;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class AuthenticationIT extends JwtTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserRoleRepository userRoles;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtProperties jwtProperties;
    @Autowired JwtService jwt;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    private static final String PASSWORD="simple password";
    private ResultActions postJson(String path,Object body) throws Exception {
        return mvc.perform(post(path).contentType("application/json").content(json.writeValueAsBytes(body)));
    }
    private JsonNode data(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsByteArray()).path("data"); }
    private String register() throws Exception {
        String email=UUID.randomUUID()+"@example.test";
        postJson("/api/v1/auth/register",Map.of("fullName","Test Customer","email",email,"phone","0901234567","password",PASSWORD))
                .andExpect(status().isCreated());
        return email;
    }
    private JsonNode login(String email) throws Exception {
        return data(postJson("/api/v1/auth/login",Map.of("email",email,"password",PASSWORD)).andExpect(status().isOk()));
    }
    private ResultActions me(String token) throws Exception { return mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+token)); }
    private ResultActions refresh(String token) throws Exception { return postJson("/api/v1/auth/refresh",Map.of("refreshToken",token)); }
    @Test void customerRegistrationNormalizesEmailHashesPasswordAndIgnoresPrivileges() throws Exception {
        String email=UUID.randomUUID()+"@example.test";
        var response=postJson("/api/v1/auth/register",Map.of("fullName","Test Customer","email","  "+email.toUpperCase(Locale.ROOT)+"  ",
                "phone","0901234567","password",PASSWORD,"role","SYSTEM_ADMIN","roles",List.of("OPERATOR_ADMIN"),"status","ACTIVE"))
                .andExpect(status().isCreated()).andExpect(jsonPath("data.role").value("CUSTOMER"))
                .andExpect(jsonPath("data.email").value(email)).andExpect(jsonPath("data.passwordHash").doesNotExist())
                .andExpect(jsonPath("data.password_hash").doesNotExist());
        User user=users.findByEmail(email).orElseThrow();
        assertThat(user.getPasswordHash()).startsWith("$2a$").isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD,user.getPasswordHash())).isTrue();
        assertThat(userRoles.findRoleCodesByUserId(user.getId())).containsExactly(RoleCode.CUSTOMER);
        assertThat(data(response).size()).isEqualTo(5);
        postJson("/api/v1/auth/register",Map.of("fullName","Other","email",email.toUpperCase(Locale.ROOT),"phone","0901234567","password",PASSWORD))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("EMAIL_ALREADY_EXISTS"));
    }
    @Test void phoneIsNotUniqueAndRequiredFieldsAreValidated() throws Exception {
        register(); register();
        postJson("/api/v1/auth/register",Map.of()).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        for (String password : List.of("short","        ","é".repeat(37)))
            postJson("/api/v1/auth/register",Map.of("fullName","Test","email",UUID.randomUUID()+"@example.test","phone","12345678","password",password))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_PASSWORD"));
    }
    @Test void loginAndCurrentUserHaveExactSafeContractsAndNoSession() throws Exception {
        String email=register(); var logged=login(email);
        assertThat(logged.size()).isEqualTo(4);
        assertThat(logged.path("user").size()).isEqualTo(4);
        assertThat(logged.path("expiresIn").asLong()).isEqualTo(3600);
        var result=me(logged.path("accessToken").asText()).andExpect(status().isOk())
                .andExpect(jsonPath("data.email").value(email)).andExpect(jsonPath("data.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("data.passwordHash").doesNotExist()).andExpect(header().doesNotExist("Set-Cookie"));
        assertThat(data(result).size()).isEqualTo(5);
        assertThat(result.andReturn().getRequest().getSession(false)).isNull();
        mvc.perform(get("/api/v1/operator/buses").header("Authorization","Bearer "+logged.path("accessToken").asText()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("ACCESS_DENIED"));
    }
    @Test void invalidCredentialsAreIndistinguishable() throws Exception {
        String email=register();
        for (String identifier: List.of(email,UUID.randomUUID()+"@example.test"))
            postJson("/api/v1/auth/login",Map.of("email",identifier,"password","wrong password"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("message").value("Invalid credentials."));
    }
    @Test void missingInvalidExpiredAndWrongTypeTokensAreRejected() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        me("invalid").andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("INVALID_CREDENTIALS"));
        String email=register(); var logged=login(email);
        me(logged.path("refreshToken").asText()).andExpect(status().isUnauthorized());
        var user=users.findByEmail(email).orElseThrow();
        var past=new JwtService(jwtProperties,Clock.fixed(Instant.now().minus(Duration.ofHours(2)),ZoneOffset.UTC));
        String expired=past.issue(new CurrentUser(user.getId(),List.of(RoleCode.CUSTOMER)),"access");
        me(expired).andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("ACCESS_TOKEN_EXPIRED"));
        mvc.perform(post("/api/v1/auth/refresh").header("Authorization","Bearer "+expired).contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("refreshToken",logged.path("refreshToken").asText())))).andExpect(status().isOk());
    }
    @Test void refreshRotatesOnceAndStoresOnlyHash() throws Exception {
        var logged=login(register()); String original=logged.path("refreshToken").asText();
        Long id=logged.path("user").path("id").asLong();
        assertThat(refreshTokens.findByTokenHashAndUserId(AuthService.hash(original),id)).isPresent();
        assertThat(refreshTokens.findByTokenHashAndUserId(original,id)).isEmpty();
        var rotated=data(refresh(original).andExpect(status().isOk()));
        assertThat(rotated.size()).isEqualTo(3);
        assertThat(rotated.path("refreshToken").asText().equals(original)).isFalse();
        me(rotated.path("accessToken").asText()).andExpect(status().isOk());
        refresh(original).andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("REFRESH_TOKEN_INVALID"));
        refresh(rotated.path("refreshToken").asText()).andExpect(status().isOk());
    }
    @Test void invalidExpiredUnpersistedAndWrongTypeRefreshAreRejected() throws Exception {
        var logged=login(register());
        var principal=new CurrentUser(logged.path("user").path("id").asLong(),List.of(RoleCode.CUSTOMER));
        var past=new JwtService(jwtProperties,Clock.fixed(Instant.now().minus(Duration.ofDays(8)),ZoneOffset.UTC));
        for (String token:List.of("invalid",logged.path("accessToken").asText(),past.issue(principal,"refresh"),jwt.issue(principal,"refresh")))
            refresh(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("REFRESH_TOKEN_INVALID"));
        postJson("/api/v1/auth/refresh",Map.of()).andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("REFRESH_TOKEN_INVALID"));
    }
    @ParameterizedTest @EnumSource(value=UserStatus.class,names={"INACTIVE","LOCKED"})
    void inactiveAndLockedUsersCannotLoginRefreshOrUseAccess(UserStatus status) throws Exception {
        String email=register(); var logged=login(email);
        users.findByEmail(email).orElseThrow().setStatus(status); em.flush();
        postJson("/api/v1/auth/login",Map.of("email",email,"password",PASSWORD)).andExpect(status().isUnauthorized());
        me(logged.path("accessToken").asText()).andExpect(status().isUnauthorized());
        refresh(logged.path("refreshToken").asText()).andExpect(status().isUnauthorized());
    }
    @Test void deletedUsersCannotAuthenticateAndCurrentRolesOverrideClaims() throws Exception {
        String email=register(); var logged=login(email); User user=users.findByEmail(email).orElseThrow();
        userRoles.saveAndFlush(new UserRole(user,roles.findByCode(RoleCode.SYSTEM_ADMIN).orElseThrow()));
        me(logged.path("accessToken").asText()).andExpect(status().isOk()).andExpect(jsonPath("data.roles.length()").value(2));
        user.setDeletedAt(LocalDateTime.now(ZoneOffset.UTC)); em.flush();
        me(logged.path("accessToken").asText()).andExpect(status().isUnauthorized());
        refresh(logged.path("refreshToken").asText()).andExpect(status().isUnauthorized());
        postJson("/api/v1/auth/login",Map.of("email",email,"password",PASSWORD)).andExpect(status().isUnauthorized());
    }
    @Test void profileUpdatesOnlyDocumentedFieldsAndPasswordChangeRevokesAllRefreshTokens() throws Exception {
        String email=register(); var first=login(email); var second=login(email); String access=first.path("accessToken").asText();
        mvc.perform(patch("/api/v1/users/me").header("Authorization","Bearer "+access).contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("fullName","Changed Name","phone","0909999999","roles",List.of("SYSTEM_ADMIN"),"email","attacker@example.test"))))
                .andExpect(status().isOk()).andExpect(jsonPath("data.fullName").value("Changed Name"))
                .andExpect(jsonPath("data.email").value(email)).andExpect(jsonPath("data.roles[0]").value("CUSTOMER"));
        mvc.perform(post("/api/v1/users/me/change-password").header("Authorization","Bearer "+access).contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("currentPassword","wrong password","newPassword","new password"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/users/me/change-password").header("Authorization","Bearer "+access).contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("currentPassword",PASSWORD,"newPassword","new password"))))
                .andExpect(status().isNoContent());
        refresh(first.path("refreshToken").asText()).andExpect(status().isUnauthorized());
        refresh(second.path("refreshToken").asText()).andExpect(status().isUnauthorized());
        me(access).andExpect(status().isOk());
        postJson("/api/v1/auth/login",Map.of("email",email,"password",PASSWORD)).andExpect(status().isUnauthorized());
        postJson("/api/v1/auth/login",Map.of("email",email,"password","new password")).andExpect(status().isOk());
    }
    @Test void refreshHashUniquenessIsEnforcedByMysql() throws Exception {
        var logged=login(register()); em.flush();
        assertThatThrownBy(()->jdbc.update("INSERT INTO refresh_tokens(user_id,token_hash,expires_at,created_at) SELECT user_id,token_hash,expires_at,created_at FROM refresh_tokens WHERE user_id=?",logged.path("user").path("id").asLong()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void refreshForeignKeyIsEnforcedByMysql() throws Exception {
        var logged=login(register()); em.flush();
        assertThatThrownBy(()->jdbc.update("UPDATE refresh_tokens SET user_id=-1 WHERE user_id=?",logged.path("user").path("id").asLong()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
