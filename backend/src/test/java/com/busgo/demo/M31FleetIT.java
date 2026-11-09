package com.busgo.demo;

import com.busgo.JwtTestSupport;
import com.busgo.demo.m31.*;
import com.busgo.auth.AuthDtos.LoginRequest;
import com.busgo.auth.AuthService;
import com.busgo.common.security.*;
import com.busgo.user.entity.RoleCode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="busgo.m31.catalogue.verified-schema=busgo_m312_test")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="busgo.m31.fleet.integration",matches="true")
class M31FleetIT extends JwtTestSupport {
    private static final Map<String,String> SECRETS=new HashMap<>();
    static { M31Fleet.accounts().stream().filter(a->!a.legacy()).forEach(a->SECRETS.put(a.secretProperty(),UUID.randomUUID().toString())); }
    @DynamicPropertySource static void secrets(DynamicPropertyRegistry registry) { SECRETS.forEach((key,value)->registry.add(key,()->value)); }
    @Autowired M31FleetService fleet;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired AuthService auth;

    @Test void dryRunCreateOnlyAndExplicitBlankProfileFillPreserveCatalogueAndCommerce() {
        assertThat(count("buses")).as("Use the unchanged M31.2 test baseline, never reset it").isEqualTo(7);
        var before=snapshot(jdbc);
        var dry=fleet.dryRun(false);
        assertThat(dry.count("BUS","CREATE")).isEqualTo(12);
        assertThat(dry.count("ACCOUNT","CREATE")).isEqualTo(3);
        assertThat(dry.count("PROFILE","PROPOSE")).isEqualTo(3);
        assertThat(snapshot(jdbc)).isEqualTo(before);
        var applied=fleet.apply(false);
        assertThat(applied.count("BUS","CREATED")).isEqualTo(12);
        assertThat(applied.count("ACCOUNT","CREATED")).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transport_operators WHERE public_description IS NOT NULL",Long.class)).isZero();
        assertThat(fleet.apply(true).count("PROFILE","FILLED_BLANK")).isEqualTo(3);
        assertThat(count("buses")).isEqualTo(19);
        assertThat(count("transport_operators")).isEqualTo(3);
        assertThat(count("bus_types")).isEqualTo(3);
        assertThat(count("seat_templates")).isEqualTo(96);
        assertThat(count("users")).isEqualTo(6);
        var after=snapshot(jdbc);
        for(var t:before.keySet()) {
            if(Set.of("transport_operators","users","user_roles","operator_staff","buses").contains(t)) continue;
            assertThat(after.get(t)).as(t).isEqualTo(before.get(t));
        }
        for(var t:List.of("users","user_roles","operator_staff","buses")) assertThat(after.get(t)).containsAll(before.get(t));
        var repeated=fleet.apply(true);
        assertThat(repeated.entries()).allMatch(e->e.action().equals("REUSE"));
        assertThat(snapshot(jdbc)).isEqualTo(after);
    }
    @Test void ownerAndAccountCollisionsSkipTheirOperatorAndPreserveUnrelatedIdentities() {
        long mt=operator(M31Fleet.MT);
        long type=jdbc.queryForObject("SELECT id FROM bus_types WHERE name='Limousine 22 chỗ'",Long.class);
        jdbc.update("INSERT INTO buses(operator_id,bus_type_id,license_plate,status,created_at,updated_at) VALUES(?,?,?,'AVAILABLE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",mt,type,"DEMO-M31-AP-01");
        jdbc.update("INSERT INTO users(full_name,email,phone,password_hash,status,created_at,updated_at) VALUES('Unrelated','operator.admin@taynguyen-demo.example','DO-NOT-CHANGE','not-a-login-secret','LOCKED',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        em.clear(); var before=snapshot(jdbc);
        var report=fleet.apply(true);
        assertThat(report.entries()).anyMatch(e->e.owner().equals(M31Fleet.AP)&&e.action().equals("CONFLICT"));
        assertThat(report.entries()).anyMatch(e->e.owner().equals(M31Fleet.TN)&&e.action().equals("CONFLICT"));
        assertThat(report.count("BUS","CREATED")).isEqualTo(4);
        assertThat(report.count("ACCOUNT","CREATED")).isEqualTo(1);
        for(var t:List.of("users","buses")) assertThat(snapshot(jdbc).get(t)).containsAll(before.get(t));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operator_staff s JOIN users u ON u.id=s.user_id WHERE u.email='operator.admin@taynguyen-demo.example'",Long.class)).isZero();
    }
    @Test void customProfilesUploadedMediaAndUnavailableLegacyBusAreNeverRepaired() {
        long op=operator(M31Fleet.AP);
        jdbc.update("UPDATE transport_operators SET public_description='User-written profile',logo_url='/user-logo' WHERE id=?",op);
        jdbc.update("INSERT INTO operator_media(id,operator_id,bus_type_id,category,mime,content) VALUES(?,?,NULL,'LOGO','image/png',?)",UUID.randomUUID().toString(),op,new byte[]{1,2,3});
        jdbc.update("UPDATE buses SET status='MAINTENANCE' WHERE license_plate='51B-770.01'");
        jdbc.update("UPDATE users SET full_name='User-changed name',phone='User-changed phone',password_hash='user-changed-hash' WHERE email='operator.admin@anphu-demo.example'");
        em.clear(); var before=snapshot(jdbc);
        var report=fleet.apply(true);
        assertThat(report.entries()).anyMatch(e->e.kind().equals("PROFILE")&&e.owner().equals(M31Fleet.AP)&&e.action().equals("PRESERVE"));
        var after=snapshot(jdbc);
        assertThat(after.get("operator_media")).isEqualTo(before.get("operator_media"));
        for(var t:List.of("users","buses")) assertThat(after.get(t)).containsAll(before.get(t));
        assertThat(jdbc.queryForObject("SELECT public_description FROM transport_operators WHERE id=?",String.class,op)).isEqualTo("User-written profile");
        assertThat(jdbc.queryForObject("SELECT logo_url FROM transport_operators WHERE id=?",String.class,op)).isEqualTo("/user-logo");
    }
    @Test void newAccountsCanLoginAndFleetApisRespectRoleAndTenant() throws Exception {
        fleet.apply(true);
        for(var a:M31Fleet.accounts().stream().filter(a->!a.legacy()).toList()) {
            var login=auth.login(new LoginRequest(a.email(),SECRETS.get(a.secretProperty())));
            String token=login.accessToken();
            if(a.role()==RoleCode.OPERATOR_ADMIN) {
                mvc.perform(get("/api/v1/operator/buses").header("Authorization","Bearer "+token))
                    .andExpect(status().isOk()).andExpect(jsonPath("pagination.totalElements").value(6));
                long foreign=jdbc.queryForObject("SELECT id FROM buses WHERE license_plate='51B-770.01'",Long.class);
                mvc.perform(get("/api/v1/operator/buses/{id}",foreign).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
            } else {
                mvc.perform(get("/api/v1/operator/trips").header("Authorization","Bearer "+token)).andExpect(status().isOk());
                mvc.perform(post("/api/v1/operator/buses").header("Authorization","Bearer "+token)
                    .contentType("application/json").content("{}")).andExpect(status().isForbidden());
                mvc.perform(get("/api/v1/operator/staff").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
            }
        }
        for(var spec:M31Fleet.operators()) {
            long id=jdbc.queryForObject("SELECT u.id FROM users u JOIN operator_staff s ON s.user_id=u.id JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id WHERE s.operator_id=? AND r.code='OPERATOR_ADMIN'",Long.class,operator(spec.code()));
            var token=jwt.issue(new CurrentUser(id,List.of(RoleCode.OPERATOR_ADMIN)),"access");
            mvc.perform(get("/api/v1/operator/buses").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("pagination.totalElements").value(spec.code().equals(M31Fleet.AP)?7:6));
        }
    }
    private long operator(String code) { return jdbc.queryForObject("SELECT id FROM transport_operators WHERE code=?",Long.class,code); }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class); }
    static Map<String,List<String>> snapshot(JdbcTemplate jdbc) {
        var result=new TreeMap<String,List<String>>();
        var tables=jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'",String.class);
        for(var t:tables) {
            var rows=new ArrayList<String>();
            for(var row:jdbc.queryForList("SELECT * FROM `"+t.replace("`","``")+"`")) {
                var normalized=new TreeMap<String,String>();
                row.forEach((k,v)->normalized.put(k,v instanceof byte[] b?Base64.getEncoder().encodeToString(b):Objects.toString(v,"<null>")));
                rows.add(normalized.toString());
            }
            Collections.sort(rows); result.put(t,rows);
        }
        return result;
    }
}
