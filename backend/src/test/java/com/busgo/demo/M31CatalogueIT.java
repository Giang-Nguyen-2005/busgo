package com.busgo.demo;

import com.busgo.JwtTestSupport;
import com.busgo.demo.m31.*;
import com.busgo.location.LocationService;
import com.busgo.location.entity.Location;
import com.busgo.location.repository.LocationRepository;
import com.busgo.common.entity.ActiveStatus;
import com.busgo.marketplace.MarketplaceRepository;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="busgo.m31.catalogue.verified-schema=busgo_m312_test")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="busgo.m31.integration",matches="true")
class M31CatalogueIT extends JwtTestSupport {
    @Autowired ConfigurableApplicationContext context;
    @Autowired M31CatalogueService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired LocationRepository locations;
    @Autowired LocationService locationService;
    @Autowired MarketplaceRepository marketplace;

    @BeforeEach void legacy() { context.getAutowireCapableBeanFactory().createBean(DemoDataSeeder.class).seed(); }

    @Test void dryRunPilotFullAndRerunPreserveLegacyAndKeepNewRoutesPrivate() {
        var before=snapshot();
        var discovery=marketplace.routes(null,8);
        var dry=service.dryRun(M31Catalogue.Batch.PILOT);
        assertThat(dry.count("LOCATION","CREATE")).isEqualTo(2);
        assertThat(dry.count("ROUTE","CREATE")).isEqualTo(4);
        assertThat(snapshot()).isEqualTo(before);
        var pilot=service.apply(M31Catalogue.Batch.PILOT);
        assertThat(pilot.count("LOCATION","CREATED")).isEqualTo(2);
        assertThat(pilot.count("ROUTE","CREATED")).isEqualTo(4);
        assertThat(count("locations")).isEqualTo(8);
        assertThat(count("routes")).isEqualTo(13);
        var full=service.apply(M31Catalogue.Batch.FULL);
        assertThat(full.count("LOCATION","CREATED")).isEqualTo(4);
        assertThat(full.count("ROUTE","CREATED")).isEqualTo(12);
        assertThat(count("locations")).isEqualTo(12);
        assertThat(count("routes")).isEqualTo(25);
        assertThat(count("route_stops")).isEqualTo(52);
        var after=snapshot();
        var rerun=service.apply(M31Catalogue.Batch.FULL);
        assertThat(rerun.entries()).noneMatch(e -> e.action().equals("CREATED") || e.action().equals("CONFLICT"));
        assertThat(snapshot()).isEqualTo(after);
        for(var table:before.keySet()) assertThat(after.get(table)).containsAll(before.get(table));
        for(var table:List.of("trips","trip_stops","trip_segments","trip_seats","trip_seat_segment_inventory",
                "operator_routes","operator_route_fares","users","user_roles","operator_staff","buses","bus_types",
                "bookings","payments","tickets")) assertThat(after.get(table)).isEqualTo(before.get(table));
        assertThat(marketplace.routes(null,8)).isEqualTo(discovery);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM routes WHERE name LIKE 'Demo · %' AND id IN (SELECT route_id FROM operator_routes)",Long.class)).isZero();
        mvcResult("/api/v1/public/discovery/routes");
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM routes r JOIN route_stops p ON p.route_id=r.id AND p.stop_order=1
            JOIN route_stops d ON d.route_id=r.id AND d.stop_order=2
            WHERE r.name LIKE 'Demo · %' AND p.location_id=r.origin_location_id
              AND d.location_id=r.destination_location_id AND p.estimated_offset_minutes=0
              AND d.estimated_offset_minutes=r.estimated_duration_min
              AND p.allow_pickup=TRUE AND p.allow_dropoff=FALSE
              AND d.allow_dropoff=TRUE AND d.allow_pickup=FALSE
            """,Long.class)).isEqualTo(16);
    }
    @Test void conflictingLocationSkipsDependentRoutesWithoutOverwriting() {
        Location l=new Location(); l.setName("Điểm đón demo Cần Thơ"); l.setProvince("USER OWNED");
        l.setStatus(ActiveStatus.INACTIVE); locations.saveAndFlush(l);
        var before=snapshot();
        var report=service.apply(M31Catalogue.Batch.PILOT);
        assertThat(report.entries()).filteredOn(e->e.action().equals("CONFLICT")).hasSize(3);
        assertThat(report.count("LOCATION","CREATED")).isEqualTo(1);
        assertThat(report.count("ROUTE","CREATED")).isEqualTo(2);
        assertThat(l.getProvince()).isEqualTo("USER OWNED");
        for(var table:before.keySet()) assertThat(snapshot().get(table)).containsAll(before.get(table));
    }
    @Test void changedRouteStopsAndStatusesArePreserved() {
        service.apply(M31Catalogue.Batch.PILOT);
        jdbc.update("UPDATE route_stops SET estimated_offset_minutes=1 WHERE route_id=(SELECT id FROM routes WHERE name='Demo · TP. Hồ Chí Minh → Cần Thơ') AND stop_order=1");
        jdbc.update("UPDATE locations SET status='INACTIVE' WHERE name='Điểm đón demo Vũng Tàu'");
        jdbc.update("UPDATE routes SET status='INACTIVE' WHERE name='Demo · Vũng Tàu → TP. Hồ Chí Minh'");
        em.clear(); var before=snapshot();
        var dry=service.dryRun(M31Catalogue.Batch.PILOT);
        var report=service.apply(M31Catalogue.Batch.PILOT);
        assertThat(report.count("ROUTE","CONFLICT")).isEqualTo(1);
        assertThat(dry.count("ROUTE","CONFLICT")).isEqualTo(1);
        assertThat(snapshot()).isEqualTo(before);
        jdbc.update("UPDATE locations SET status='INACTIVE' WHERE name='Bến xe TP. Hồ Chí Minh'");
        em.clear();
        var full=service.apply(M31Catalogue.Batch.FULL);
        assertThat(full.count("ROUTE","CREATED")).isEqualTo(10);
        assertThat(full.entries()).filteredOn(e->e.reason().equals("Inactive endpoint preserved; no new route created")).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM routes WHERE name LIKE 'Demo · %Phan Thiết%'",Long.class)).isZero();
    }
    @Test void exactLookupFindsActiveIdBeyondAutocompleteAndRejectsInvalidRequests() throws Exception {
        for(int i=0;i<25;i++) {
            Location l=new Location(); l.setName("AAAA test "+i); l.setStatus(ActiveStatus.ACTIVE); locations.save(l);
        }
        Location target=new Location(); target.setName("ZZZZ destination"); target.setStatus(ActiveStatus.ACTIVE);
        locations.saveAndFlush(target);
        assertThat(locationService.search("")).noneMatch(l->l.id().equals(target.getId()));
        mvc.perform(get("/api/v1/locations/lookup").param("ids",target.getId()+",999999999"))
            .andExpect(status().isOk()).andExpect(jsonPath("data.length()").value(1))
            .andExpect(jsonPath("data[0].name").value("ZZZZ destination"));
        target.setStatus(ActiveStatus.INACTIVE); locations.flush();
        mvc.perform(get("/api/v1/locations/lookup").param("ids",target.getId().toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("data.length()").value(0));
        mvc.perform(get("/api/v1/locations/lookup").param("ids","-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/locations/lookup").param("ids",String.join(",",Collections.nCopies(101,"1"))))
            .andExpect(status().isBadRequest());
    }
    private void mvcResult(String path) {
        try { mvc.perform(get(path)).andExpect(status().isOk()); }
        catch(Exception e) { throw new AssertionError(e); }
    }
    private long count(String t) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+t,Long.class); }
    private Map<String,List<Map<String,Object>>> snapshot() {
        em.flush();
        var result=new TreeMap<String,List<Map<String,Object>>>();
        for(var t:List.of("locations","routes","route_stops","trips","trip_stops","trip_segments","trip_seats",
                "trip_seat_segment_inventory","operator_routes","operator_route_fares","users","user_roles",
                "operator_staff","buses","bus_types","bookings","payments","tickets"))
            result.put(t,jdbc.queryForList("SELECT * FROM "+t+" ORDER BY 1"));
        return result;
    }
}
