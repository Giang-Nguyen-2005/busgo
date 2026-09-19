package com.busgo;

import com.busgo.common.exception.BusinessException;
import com.busgo.common.exception.GlobalExceptionHandler;
import com.busgo.common.exception.ResourceNotFoundException;
import com.busgo.common.response.PagedResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExceptionHandlerTest {
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new FixtureController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void validationHasFieldDetails() throws Exception {
        mvc.perform(post("/fixture").contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("details.value").value("Value is required"));
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mvc.perform(post("/fixture").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("BAD_REQUEST"));
    }

    @Test
    void businessConflictUsesContract() throws Exception {
        mvc.perform(get("/fixture/conflict")).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("TEST_CONFLICT"))
                .andExpect(jsonPath("message").value("Test conflict."))
                .andExpect(jsonPath("timestamp").isString())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"details\":null")));
    }

    @Test
    void missingResourceIsNotFound() throws Exception {
        mvc.perform(get("/fixture/missing")).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("TEST_NOT_FOUND"));
    }

    @Test
    void unknownUrlIsNotFound() throws Exception {
        mvc.perform(get("/unknown")).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("NOT_FOUND"));
    }

    @Test
    void unauthorizedDoesNotLeakException() throws Exception {
        mvc.perform(get("/fixture/unauthorized")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("message").value("Authentication is required."));
    }

    @Test
    void unexpectedDoesNotLeakException() throws Exception {
        mvc.perform(get("/fixture/unexpected")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("message").value("An unexpected error occurred."))
                .andExpect(jsonPath("code").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    void paginationMatchesContract() throws Exception {
        mvc.perform(get("/fixture/page")).andExpect(status().isOk())
                .andExpect(content().json("""
                    {"data":["sample"],"pagination":{"page":0,"size":20,"totalElements":21,"totalPages":2}}
                    """, true));
    }

    // Test-only endpoints: no business API is registered in the application.
    @RestController
    static class FixtureController {
        record Input(@NotBlank(message = "Value is required") String value) {}
        @PostMapping("/fixture") void validate(@Valid @RequestBody Input input) {}
        @GetMapping("/fixture/conflict") void conflict() { throw new BusinessException("TEST_CONFLICT", "Test conflict."); }
        @GetMapping("/fixture/missing") void missing() { throw new ResourceNotFoundException("TEST_NOT_FOUND", "Missing."); }
        @GetMapping("/fixture/unauthorized") void unauthorized() { throw new BadCredentialsException("private detail"); }
        @GetMapping("/fixture/unexpected") void unexpected() { throw new IllegalStateException("private detail"); }
        @GetMapping("/fixture/page") PagedResponse<String> page() {
            return PagedResponse.from(new PageImpl<>(List.of("sample"), PageRequest.of(0, 20), 21));
        }
    }
}
