package com.busgo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.busgo.user.entity.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev") @Transactional
class M24BApiIT extends M16ATestSupport {
    @Autowired MockMvc mvc;
    private String terms() {return """
        {"code":" api-sale ","name":"API sale","discountType":"PERCENT","discountValue":10,
        "minimumSpend":0,"startsAt":"2026-01-01T00:00:00Z","endsAt":"2030-01-01T00:00:00Z",
        "totalLimit":10,"customerLimit":1}
        """;}
    @Test void adminCreatesDraftAndListIsPaginated() throws Exception {
        var f=fixture();var a=actor(f,RoleCode.OPERATOR_ADMIN);
        mvc.perform(post("/api/v1/operator/vouchers").header("Authorization",bearer(a)).contentType("application/json").content(terms())).andExpect(status().isCreated()).andExpect(jsonPath("$.data.code").value("API-SALE")).andExpect(jsonPath("$.data.state").value("DRAFT"));
        mvc.perform(get("/api/v1/operator/vouchers?q=API&page=0&size=1").header("Authorization",bearer(a))).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.pagination.totalElements").value(1));
    }
    @Test void staffReadsButCannotMutateAndSystemCannotList() throws Exception {
        var f=fixture();var staff=actor(f,RoleCode.OPERATOR_STAFF);var sys=actor(f,RoleCode.SYSTEM_ADMIN);
        mvc.perform(get("/api/v1/operator/vouchers").header("Authorization",bearer(staff))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/operator/vouchers").header("Authorization",bearer(staff)).contentType("application/json").content(terms())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/operator/vouchers").header("Authorization",bearer(sys))).andExpect(status().isForbidden());
    }
    @Test void validationRejectsNegativeAmountsAndUnboundedPages() throws Exception {
        var a=actor(fixture(),RoleCode.OPERATOR_ADMIN);
        mvc.perform(post("/api/v1/operator/vouchers").header("Authorization",bearer(a)).contentType("application/json").content(terms().replace("\"discountValue\":10","\"discountValue\":-1"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/operator/vouchers?size=1000").header("Authorization",bearer(a))).andExpect(status().isBadRequest());
    }
    @Test void malformedQuoteAndAnonymousCheckoutDenied() throws Exception {
        var c=customer("m24b-api");
        mvc.perform(post("/api/v1/bookings/voucher-quote").header("Authorization",bearer(c.user())).contentType("application/json").content("{\"holdToken\":\"x\",\"voucherCode\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/bookings/voucher-quote").contentType("application/json").content("{\"holdToken\":\"x\",\"voucherCode\":\"SALE\"}")).andExpect(status().isUnauthorized());
    }
}
