package com.busgo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FoundationTest {
    @Autowired MockMvc mvc;

    @Test
    void publicHealthReturnsExactContract() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"data\":{\"status\":\"UP\"}}", true));
    }

    @Test
    void otherEndpointsRequireAuthenticationWithoutRedirect() throws Exception {
        mvc.perform(get("/api/v1/private"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("timestamp").isString())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    @WithMockUser
    void authenticatedRequestsAreDeniedUntilAccessRulesExist() throws Exception {
        mvc.perform(get("/api/v1/private"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("ACCESS_DENIED"));
    }
}
