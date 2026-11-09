package com.busgo.demo;

import com.busgo.JwtTestSupport;
import com.busgo.demo.m31.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

/** Explicit persistent rehearsal on the already verified M31.2 schema; never resets it. */
@SpringBootTest(properties="busgo.m31.catalogue.verified-schema=busgo_m312_test")
@ActiveProfiles("dev")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="busgo.m31.fleet.acceptance",matches="true")
class M31FleetAcceptanceIT extends JwtTestSupport {
    @Autowired M31FleetService fleet;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Test void applyOnceToExistingM312BaselineAndRecordRealCounts() throws Exception {
        var before=counts(); assertThat(before.get("buses")).as("Existing M31.2 baseline; no reset/reseed").isEqualTo(7);
        var snapshot=M31FleetIT.snapshot(jdbc);
        var dry=fleet.dryRun(true);
        assertThat(dry.count("BUS","CREATE")).isEqualTo(12);
        assertThat(dry.count("ACCOUNT","CREATE")).isEqualTo(3);
        assertThat(M31FleetIT.snapshot(jdbc)).isEqualTo(snapshot);
        var applied=fleet.apply(true);
        assertThat(applied.count("BUS","CREATED")).isEqualTo(12);
        assertThat(applied.count("ACCOUNT","CREATED")).isEqualTo(3);
        assertThat(applied.count("PROFILE","FILLED_BLANK")).isEqualTo(3);
        var after=counts(); var afterRows=M31FleetIT.snapshot(jdbc);
        var rerun=fleet.apply(true);
        assertThat(rerun.entries()).allMatch(e->e.action().equals("REUSE"));
        assertThat(M31FleetIT.snapshot(jdbc)).isEqualTo(afterRows);
        for(var t:snapshot.keySet()) if(!Set.of("transport_operators","users","user_roles","operator_staff","buses").contains(t))
            assertThat(afterRows.get(t)).as(t).isEqualTo(snapshot.get(t));
        var receipt=new LinkedHashMap<String,Object>();
        receipt.put("schema","busgo_m312_test"); receipt.put("before",before); receipt.put("dry",dry);
        receipt.put("applied",applied); receipt.put("after",after); receipt.put("rerun",rerun);
        Files.writeString(Path.of("target/m31-3-acceptance.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(receipt));
    }
    private Map<String,Long> counts() {
        var result=new TreeMap<String,Long>();
        for(var t:List.of("transport_operators","buses","bus_types","seat_templates","users","user_roles","operator_staff",
                "locations","routes","operator_routes","operator_route_fares","trips","trip_seat_segment_inventory","bookings","payments","tickets","operator_media"))
            result.put(t,jdbc.queryForObject("SELECT COUNT(*) FROM "+t,Long.class));
        return result;
    }
}
