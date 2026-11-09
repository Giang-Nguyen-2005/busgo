package com.busgo.demo;

import com.busgo.JwtTestSupport;
import com.busgo.demo.m31.*;
import com.busgo.media.OperatorMediaService;
import com.busgo.operations.*;
import com.busgo.promotion.*;
import com.busgo.common.security.CurrentUser;
import com.busgo.user.entity.RoleCode;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="busgo.m31.catalogue.verified-schema=busgo_m312_test") @ActiveProfiles("dev")
@Transactional
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="busgo.m31.support.integration",matches="true")
class M31SupportIT extends JwtTestSupport {
    @Autowired M31SupportService support;
    @Autowired JdbcTemplate db;
    @Autowired OperatorMediaService media;
    @Autowired VoucherService vouchers;
    @Autowired OperationsService operations;
    long owner;CurrentUser actor;
    @BeforeEach void actor(){
        owner=db.queryForObject("SELECT id FROM transport_operators WHERE code='DEMO-ANPHU'",Long.class);
        long id=db.queryForObject("SELECT s.user_id FROM operator_staff s JOIN user_roles ur ON ur.user_id=s.user_id JOIN roles r ON r.id=ur.role_id WHERE s.operator_id=? AND r.code='OPERATOR_ADMIN'",Long.class,owner);
        actor=new CurrentUser(id,List.of(RoleCode.OPERATOR_ADMIN));
        var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(actor,null,actor.authorities()));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
    }
    @AfterEach void clearActor(){org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    @Test void editedGalleryPausedVoucherAndHistoricalCrewArePreserved() throws Exception {
        var p=media.gallery(owner).photos().get(0);
        media.edit(actor,p.busTypeId(),UUID.fromString(p.id()),OperatorMediaService.Category.OTHER,"Ảnh đã chỉnh bởi người dùng",false);
        long voucher=db.queryForObject("SELECT id FROM operator_vouchers WHERE code='M31-AP-FIX20'",Long.class);
        vouchers.state(actor,voucher,false);
        var snapshot=M31FleetIT.snapshot(db);
        var report=support.apply(Path.of("../frontend/public/images/busgo"));
        assertThat(report.entries()).allMatch(e->e.action().equals("PRESERVE"));
        assertThat(M31FleetIT.snapshot(db)).isEqualTo(snapshot);
    }
    @Test void voucherConditionsPrivateCodesAndFutureStateUseTheRealEvaluator() {
        long apRoute=db.queryForObject("SELECT operator_route_id FROM operator_vouchers WHERE code='M31-AP-MIN50'",Long.class);
        // Customer is real smoke customer; quotes do not reserve or redeem usages.
        long customer=db.queryForObject("SELECT MIN(customer_id) FROM voucher_usages",Long.class);
        var before=M31FleetIT.snapshot(db);
        assertThatThrownBy(()->vouchers.evaluate(owner,apRoute,customer,"M31-AP-MIN50",new BigDecimal("290000"),1,false)).extracting("code").isEqualTo("VOUCHER_MINIMUM_SPEND");
        long tn=db.queryForObject("SELECT id FROM transport_operators WHERE code='DEMO-TAYNGUYEN'",Long.class);
        long route=db.queryForObject("SELECT MIN(id) FROM operator_routes WHERE operator_id=?",Long.class,tn);
        var privateQuote=vouchers.evaluate(tn,route,customer,"M31-TN-PRIVATE15",new BigDecimal("200000"),1,false).quote();
        assertThat(privateQuote.eligible()).isTrue();assertThat(privateQuote.discountAmount()).isEqualByComparingTo("15000");
        assertThatThrownBy(()->vouchers.evaluate(tn,route,customer,"M31-TN-PAUSED10",new BigDecimal("200000"),1,false)).extracting("code").isEqualTo("VOUCHER_INACTIVE");
        long mt=db.queryForObject("SELECT id FROM transport_operators WHERE code='DEMO-MINHTHANH'",Long.class);
        long mtRoute=db.queryForObject("SELECT MIN(id) FROM operator_routes WHERE operator_id=?",Long.class,mt);
        assertThat(vouchers.evaluate(mt,mtRoute,customer,"M31-MT-PCT10",new BigDecimal("300000"),1,false).quote().discountAmount()).isEqualByComparingTo("30000");
        assertThat(vouchers.evaluate(mt,mtRoute,customer,"M31-MT-PCT10",new BigDecimal("900000"),1,false).quote().discountAmount()).isEqualByComparingTo("50000");
        assertThatThrownBy(()->vouchers.evaluate(mt,mtRoute,customer,"M31-MT-FUTURE10",new BigDecimal("200000"),1,false)).extracting("code").isEqualTo("VOUCHER_NOT_STARTED");
        assertThat(M31FleetIT.snapshot(db)).isEqualTo(before);
    }
    @Test void overlappingAndForeignCrewAreRejectedWithoutWrites() {
        long employee=db.queryForObject("SELECT id FROM operator_employees WHERE employee_code='M31-AP-DRIVER'",Long.class);
        long assigned=db.queryForObject("SELECT trip_id FROM trip_crew_assignments WHERE employee_id=?",Long.class,employee);
        long conflict=db.queryForObject("SELECT t.id FROM trips t JOIN operator_routes r ON r.id=t.operator_route_id JOIN trips a ON a.id=? WHERE r.operator_id=? AND t.id<>a.id AND t.departure_time<a.estimated_arrival_time AND t.estimated_arrival_time>a.departure_time ORDER BY t.id LIMIT 1",Long.class,assigned,owner);
        var before=M31FleetIT.snapshot(db);
        assertThatThrownBy(()->operations.replaceCrew(actor,conflict,new OperationsDtos.CrewReplacement(List.of(new OperationsDtos.CrewInput(employee,OperationsDtos.Capability.DRIVER))))).hasMessageContaining("overlapping");
        long foreign=db.queryForObject("SELECT id FROM operator_employees WHERE employee_code='M31-MT-DRIVER'",Long.class);
        assertThatThrownBy(()->operations.replaceCrew(actor,conflict,new OperationsDtos.CrewReplacement(List.of(new OperationsDtos.CrewInput(foreign,OperationsDtos.Capability.DRIVER))))).hasMessageContaining("not found");
        assertThat(M31FleetIT.snapshot(db)).isEqualTo(before);
    }
    @Test void invalidMediaSizeMimeQuotaAndUnoperatedTypeAreRejected() {
        long type=media.gallery(owner).photos().get(0).busTypeId();
        var before=M31FleetIT.snapshot(db);
        var fake=new org.springframework.mock.web.MockMultipartFile("files","fake.jpg","image/jpeg",new byte[]{1,2,3});
        var oversized=new org.springframework.mock.web.MockMultipartFile("files","large.jpg","image/jpeg",new byte[5*1024*1024+1]);
        for(var file:List.of(fake,oversized)) assertThatThrownBy(()->media.upload(actor,type,OperatorMediaService.Category.OTHER,"Invalid demo test",List.of(file))).isInstanceOf(com.busgo.common.exception.BusinessException.class);
        assertThatThrownBy(()->media.upload(actor,type,OperatorMediaService.Category.OTHER,"Quota test",Collections.nCopies(12,fake))).isInstanceOf(com.busgo.common.exception.BusinessException.class);
        long limo=db.queryForObject("SELECT id FROM bus_types WHERE name=?",Long.class,M31Fleet.types().get("LIMO22"));
        assertThatThrownBy(()->media.upload(actor,limo,OperatorMediaService.Category.OTHER,"Wrong type",List.of(fake))).extracting("code").isEqualTo("MEDIA_TYPE_NOT_OPERATED");
        assertThat(M31FleetIT.snapshot(db)).isEqualTo(before);
    }
}
