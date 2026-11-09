package com.busgo.demo;

import com.busgo.JwtTestSupport;
import com.busgo.demo.m31.*;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "busgo.m31.catalogue.verified-schema=busgo_m312_test","server.address=127.0.0.1","busgo.notifications.initial-delay=PT24H"})
@ActiveProfiles("dev")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="busgo.m31.support.acceptance",matches="true")
class M31SupportAcceptanceIT extends JwtTestSupport {
    @Autowired M31SupportService support;
    @Autowired JdbcTemplate db;
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired com.busgo.common.security.JwtService jwt;
    private final Path assets=Path.of("../frontend/public/images/busgo");
    @Test void importAndVerifySupportingContentAndOneVoucherBooking() throws Exception {
        var before=counts();var original=M31FleetIT.snapshot(db);
        var dry=support.dryRun(assets);
        assertThat(M31FleetIT.snapshot(db)).isEqualTo(original);
        var applied=support.apply(assets);
        Files.writeString(Path.of("target/m31-5-import-receipt.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("before",before,"dry",dry,"apply",applied,"after",counts())));
        var imported=M31FleetIT.snapshot(db);
        for(var table:List.of("buses","bus_types","locations","routes","route_stops","operator_routes","operator_route_fares","trips","trip_stops","trip_segments","trip_seats","trip_seat_segment_inventory","bookings","payments","tickets","users"))
            assertThat(imported.get(table)).as(table+" preserved").isEqualTo(original.get(table));
        for(var table:List.of("operator_employees","driver_profiles","employee_capabilities","trip_crew_assignments"))
            assertThat(imported.get(table)).containsAll(original.get(table));
        assertThat(counts().get("operator_media")).isEqualTo(11);
        assertThat(counts().get("operator_vouchers")).isEqualTo(6);
        assertThat(counts().get("operator_employees")).isEqualTo(8);
        assertThat(counts().get("trip_crew_assignments")).isEqualTo(12);
        var rerun=support.apply(assets);
        assertThat(rerun.entries()).allMatch(e->e.action().equals("PRESERVE"));
        assertThat(M31FleetIT.snapshot(db)).isEqualTo(imported);
        assertThat(get("/api/v1/public/operators",null).path("data").size()).isEqualTo(3);
        var promotions=get("/api/v1/public/promotions?size=20",null);
        assertThat(promotions.path("data").size()).isEqualTo(3);
        assertThat(promotions.toString()).doesNotContain("PRIVATE15","PAUSED10","FUTURE10");
        var owners=new ArrayList<Long>();var tokens=new ArrayList<String>();
        for(var spec:M31Fleet.operators()) {
            long owner=db.queryForObject("SELECT id FROM transport_operators WHERE code=?",Long.class,spec.code());owners.add(owner);
            var gallery=get("/api/v1/public/operators/"+owner+"/media",null).path("data");
            assertThat(gallery.path("logoUrl").asText()).startsWith("/api/v1/public/operators/"+owner+"/media/");
            binary(gallery.path("logoUrl").asText(),null,200);
            for(var photo:gallery.path("photos")) {
                assertThat(photo.path("alt").asText()).startsWith("[DEMO M31]");
                assertThat(photo.path("featured").asBoolean()).isTrue();
                binary(photo.path("url").asText(),null,200);
            }
            assertThat(get("/api/v1/public/operators/"+owner+"/profile",null).toString()).contains("demo");
            // Generate a test JWT for an existing dedicated admin; no password reset or credential exposure.
            long user=db.queryForObject("SELECT s.user_id FROM operator_staff s JOIN user_roles ur ON ur.user_id=s.user_id JOIN roles r ON r.id=ur.role_id WHERE s.operator_id=? AND r.code='OPERATOR_ADMIN'",Long.class,owner);
            String token=token(user);tokens.add(token);
            assertThat(get("/api/v1/operator/marketplace-profile",token).path("data").path("id").asLong()).isEqualTo(owner);
            assertThat(get("/api/v1/operator/vouchers",token).path("data").size()).isEqualTo(2);
            assertThat(get("/api/v1/operator/employees",token).path("data").size()).isGreaterThanOrEqualTo(2);
            for(var photo:gallery.path("photos")) assertThat(get("/api/v1/operator/bus-types/"+photo.path("busTypeId").asLong()+"/media",token).path("data").size()).isEqualTo(1);
            var crewTrips=db.queryForList("SELECT DISTINCT a.trip_id FROM trip_crew_assignments a JOIN operator_employees e ON e.id=a.employee_id WHERE e.operator_id=? AND e.employee_code LIKE 'M31-%'",Long.class,owner);
            assertThat(crewTrips).hasSize(1);
            assertThat(get("/api/v1/operator/trips/"+crewTrips.get(0)+"/crew",token).path("data").path("ready").asBoolean()).isTrue();
        }
        long foreignVoucher=db.queryForObject("SELECT id FROM operator_vouchers WHERE code='M31-MT-PCT10'",Long.class);
        request("/api/v1/operator/vouchers/"+foreignVoucher,HttpMethod.GET,null,tokens.get(0),404);
        long foreignEmployee=db.queryForObject("SELECT id FROM operator_employees WHERE employee_code='M31-MT-DRIVER'",Long.class);
        request("/api/v1/operator/employees/"+foreignEmployee,HttpMethod.GET,null,tokens.get(0),404);
        var foreignPhoto=get("/api/v1/public/operators/"+owners.get(1)+"/media",null).path("data").path("photos").get(0);
        binary("/api/v1/operator/bus-types/"+foreignPhoto.path("busTypeId").asLong()+"/media/"+foreignPhoto.path("id").asText(),tokens.get(0),404);
        binary("/api/v1/public/operators/"+owners.get(0)+"/media/"+foreignPhoto.path("id").asText(),null,404);

        // A failed later assertion/rerun must never produce a second booking: durable namespace evidence wins.
        var prior=db.queryForList("SELECT b.id FROM bookings b JOIN voucher_usages u ON u.booking_id=b.id JOIN operator_vouchers v ON v.id=u.voucher_id WHERE v.code='M31-AP-FIX20'",Long.class);
        long booking;
        if(prior.isEmpty()) booking=bookOne();
        else {assertThat(prior).hasSize(1);booking=prior.get(0);}
        assertThat(db.queryForObject("SELECT state FROM voucher_usages WHERE booking_id=?",String.class,booking)).isEqualTo("REDEEMED");
        assertThat(db.queryForObject("SELECT amount FROM payments WHERE booking_id=?",java.math.BigDecimal.class,booking)).isEqualByComparingTo("160000");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM tickets WHERE booking_id=? AND status='VALID'",Long.class,booking)).isEqualTo(1);
        var paid=M31FleetIT.snapshot(db);support.apply(assets);assertThat(M31FleetIT.snapshot(db)).isEqualTo(paid);
        // The old transaction and all its child rows survive byte-for-byte.
        for(var table:List.of("bookings","booking_items","payments","tickets")) assertThat(paid.get(table)).containsAll(original.get(table));
        assertThat(counts().get("bookings")).isEqualTo(2);
        Files.writeString(Path.of("target/m31-5-acceptance.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("before",before,"after",counts(),"dry",dry,"apply",applied,"rerun",rerun,"bookingSmoke",Map.of("bookingId",booking,"gross",180000,"discount",20000,"net",160000,"redeemed",true,"validQrPresent",true))));
    }
    private long bookOne() throws Exception {
        var places=new HashMap<String,Long>();
        for(var p:M31Catalogue.places(M31Catalogue.Batch.FULL)) places.put(p.key(),db.queryForObject("SELECT id FROM locations WHERE name=?",Long.class,p.name()));
        long from=places.get("HCM"),to=places.get("PHANTHIET");
        var row=get("/api/v1/trips/search?pickupLocationId="+from+"&dropoffLocationId="+to+"&departureDate=2026-11-14",null).path("data").get(0);
        long trip=row.path("tripId").asLong();String journey="?pickupLocationId="+from+"&dropoffLocationId="+to;
        var detail=get("/api/v1/trips/"+trip+journey,null).path("data");
        assertThat(detail.toString()).contains("Điểm đón demo Phan Thiết","departureTime","arrivalTime");
        assertThat(get("/api/v1/trips/"+trip+"/promotions"+journey,null).path("data").size()).isEqualTo(1);
        var seats=get("/api/v1/trips/"+trip+"/seats"+journey,null).path("data").path("seats");
        long seat=0;
        for(var s:seats) if(db.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory WHERE trip_seat_id=? AND status<>'AVAILABLE'",Long.class,s.path("tripSeatId").asLong())==0) {seat=s.path("tripSeatId").asLong();break;}
        assertThat(seat).isPositive();
        var customers=db.queryForList("SELECT id FROM users WHERE email LIKE 'm31-support-%@demo.example'",Long.class);
        String email,token;
        if(customers.isEmpty()) {
            email="m31-support-"+UUID.randomUUID()+"@demo.example";String password=UUID.randomUUID().toString();
            post("/api/v1/auth/register",Map.of("fullName","Khách voucher demo M31","email",email,"phone","DEMO-CUSTOMER","password",password),null,201);
            token=post("/api/v1/auth/login",Map.of("email",email,"password",password),null,200).path("data").path("accessToken").asText();
        } else {
            assertThat(customers).hasSize(1);long user=customers.get(0);
            email=db.queryForObject("SELECT email FROM users WHERE id=?",String.class,user);
            token=jwt.issue(new com.busgo.common.security.CurrentUser(user,List.of(com.busgo.user.entity.RoleCode.CUSTOMER)),"access");
            for(String pending:db.queryForList("SELECT DISTINCT hold_token FROM trip_seat_segment_inventory WHERE held_by_user_id=? AND status='HELD'",String.class,user)) {
                var release=http.exchange("/api/v1/seat-holds/"+pending,HttpMethod.DELETE,new HttpEntity<>(headers(token)),Void.class);
                assertThat(release.getStatusCode().value()).isEqualTo(204);
            }
        }
        String hold=post("/api/v1/seat-holds",Map.of("tripId",trip,"pickupLocationId",from,"dropoffLocationId",to,"tripSeatIds",List.of(seat)),token,201).path("data").path("holdToken").asText();
        var options=post("/api/v1/bookings/voucher-options",Map.of("holdToken",hold),token,200).path("data");assertThat(options.path("options").size()).isEqualTo(1);
        var quote=post("/api/v1/bookings/voucher-quote",Map.of("holdToken",hold,"voucherCode","M31-AP-FIX20"),token,200).path("data");
        assertThat(quote.path("eligible").asBoolean()).isTrue();assertThat(quote.path("grossAmount").decimalValue()).isEqualByComparingTo("180000");
        assertThat(quote.path("discountAmount").decimalValue()).isEqualByComparingTo("20000");assertThat(quote.path("totalAmount").decimalValue()).isEqualByComparingTo("160000");
        for(String wrong:List.of("M31-MT-PCT10","M31-TN-PRIVATE15","M31-TN-PAUSED10")) {
            post("/api/v1/bookings/voucher-quote",Map.of("holdToken",hold,"voucherCode",wrong),token,409);
        }
        var created=post("/api/v1/bookings",Map.of("holdToken",hold,"voucherCode","M31-AP-FIX20","contactName","Khách voucher demo M31","contactPhone","DEMO-CUSTOMER","contactEmail",email),token,201).path("data");
        long booking=created.path("bookingId").asLong();assertThat(created.path("price").path("totalAmount").decimalValue()).isEqualByComparingTo("160000");
        var payment=post("/api/v1/bookings/"+booking+"/payments/mock-confirm",Map.of(),token,200).path("data");
        assertThat(payment.path("paymentStatus").asText()).isEqualTo("PAID");assertThat(payment.path("amount").decimalValue()).isEqualByComparingTo("160000");
        var ticket=get("/api/v1/bookings/"+booking+"/ticket",token).path("data");
        assertThat(ticket.path("tickets").get(0).path("status").asText()).isEqualTo("VALID");assertThat(ticket.path("tickets").get(0).path("qrData").asText()).isNotBlank();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory WHERE trip_seat_id=? AND status='BOOKED'",Long.class,seat)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory WHERE hold_token=?",Long.class,hold)).isZero();
        return booking;
    }
    private String token(long user) {
        return jwt.issue(new com.busgo.common.security.CurrentUser(user,List.of(com.busgo.user.entity.RoleCode.OPERATOR_ADMIN)),"access");
    }
    private JsonNode get(String path,String token) throws Exception{return request(path,HttpMethod.GET,null,token,200);}
    private JsonNode post(String path,Object body,String token,int status) throws Exception{return request(path,HttpMethod.POST,body,token,status);}
    private HttpHeaders headers(String token){var h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);if(token!=null)h.setBearerAuth(token);return h;}
    private JsonNode request(String path,HttpMethod method,Object body,String token,int expected) throws Exception {
        var r=http.exchange(path,method,new HttpEntity<>(body,headers(token)),String.class);
        assertThat(r.getStatusCode().value()).as(method+" "+path).isEqualTo(expected);return json.readTree(r.getBody());
    }
    private void binary(String path,String token,int expected){var r=http.exchange(path,HttpMethod.GET,new HttpEntity<>(headers(token)),byte[].class);assertThat(r.getStatusCode().value()).as(path).isEqualTo(expected);if(expected==200){assertThat(r.getBody()).isNotEmpty();assertThat(r.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");}}
    private Map<String,Long> counts(){var result=new TreeMap<String,Long>();for(var t:List.of("transport_operators","buses","bus_types","seat_templates","locations","routes","route_stops","operator_routes","operator_route_fares","trips","trip_stops","trip_segments","trip_seats","trip_seat_segment_inventory","users","bookings","payments","tickets","operator_media","operator_vouchers","voucher_usages","operator_employees","driver_profiles","employee_capabilities","trip_crew_assignments"))result.put(t,db.queryForObject("SELECT COUNT(*) FROM "+t,Long.class));return result;}
}
