package com.busgo.demo;

import com.busgo.JwtTestSupport;
import com.busgo.demo.m31.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Explicit one-time rehearsal, committed only in the separately verified M31 test schema.
 * Run separately after the rollback-based focused tests. Never points at the application DB.
 */
@SpringBootTest(properties="busgo.m31.catalogue.verified-schema=busgo_m312_test")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="busgo.m31.acceptance",matches="true")
class M31CatalogueAcceptanceIT extends JwtTestSupport {
    @Autowired ConfigurableApplicationContext context;
    @Autowired M31CatalogueService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test void prepareIsolatedCatalogueAndWriteReceipt() throws Exception {
        // Validate the target read-only before the legacy fixture is ever invoked.
        service.dryRun(M31Catalogue.Batch.PILOT);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM locations",Long.class))
                .as("Acceptance requires an empty, separately prepared test catalogue; never resets it").isZero();
        context.getAutowireCapableBeanFactory().createBean(DemoDataSeeder.class).seed();
        var before=counts();
        var pilotDry=service.dryRun(M31Catalogue.Batch.PILOT);
        assertThat(counts()).isEqualTo(before);
        var pilot=service.apply(M31Catalogue.Batch.PILOT);
        var pilotCounts=counts();
        var fullDry=service.dryRun(M31Catalogue.Batch.FULL);
        assertThat(counts()).isEqualTo(pilotCounts);
        var full=service.apply(M31Catalogue.Batch.FULL);
        var after=counts();
        var rerun=service.apply(M31Catalogue.Batch.FULL);
        assertThat(counts()).isEqualTo(after);
        assertThat(after.get("locations")).isEqualTo(12);
        assertThat(after.get("routes")).isEqualTo(25);
        assertThat(after.get("route_stops")).isEqualTo(52);
        assertThat(rerun.entries()).noneMatch(e->e.action().equals("CREATED") || e.action().equals("CONFLICT"));
        for(var t:before.keySet()) if(!Set.of("locations","routes","route_stops").contains(t))
            assertThat(after.get(t)).as(t).isEqualTo(before.get(t));
        mvc.perform(get("/api/v1/locations")).andExpect(status().isOk()).andExpect(jsonPath("data.length()").value(12));
        var id=jdbc.queryForObject("SELECT id FROM locations WHERE name='Điểm đón demo Pleiku'",Long.class);
        mvc.perform(get("/api/v1/locations/lookup").param("ids",id.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("data[0].name").value("Điểm đón demo Pleiku"));
        var discovery=mvc.perform(get("/api/v1/public/discovery/routes")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(discovery).doesNotContain("Demo ·");
        var receipt=new LinkedHashMap<String,Object>();
        receipt.put("schema","busgo_m312_test"); receipt.put("before",before);
        receipt.put("pilotDry",pilotDry); receipt.put("pilot",pilot); receipt.put("pilotCounts",pilotCounts);
        receipt.put("fullDry",fullDry); receipt.put("full",full); receipt.put("after",after); receipt.put("rerun",rerun);
        receipt.put("apiSmoke","MockMvc + Spring Security + real MySQL: autocomplete/exact lookup/discovery passed");
        Files.writeString(Path.of("target/m31-2-acceptance.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(receipt));
    }
    private Map<String,Long> counts() {
        var result=new TreeMap<String,Long>();
        for(var t:List.of("locations","routes","route_stops","transport_operators","operator_staff","users",
                "bus_types","seat_templates","buses","operator_routes","operator_route_fares","trips",
                "trip_stops","trip_segments","trip_seats","trip_seat_segment_inventory","bookings","payments","tickets"))
            result.put(t,jdbc.queryForObject("SELECT COUNT(*) FROM "+t,Long.class));
        return result;
    }
}
