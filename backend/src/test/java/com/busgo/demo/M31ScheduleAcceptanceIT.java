package com.busgo.demo;

import com.busgo.JwtTestSupport;
import com.busgo.demo.m31.*;
import com.busgo.common.time.BusGoTime;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

/** One persistent, explicitly opted-in import + real loopback HTTP commerce rehearsal. Never resets data. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "busgo.m31.catalogue.verified-schema=busgo_m312_test","server.address=127.0.0.1",
    "busgo.notifications.initial-delay=PT24H"})
@ActiveProfiles("dev")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="busgo.m31.schedule.acceptance",matches="true")
class M31ScheduleAcceptanceIT extends JwtTestSupport {
    @Autowired M31ScheduleService schedules;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;

    @Test void importFixedWindowThenSearchHoldQuoteBookMockPayAndReadQrOverRealHttp() throws Exception {
        LocalDate anchor=LocalDate.parse(System.getProperty("busgo.m31.schedule.anchor"));
        var before=counts(); assertThat(before.get("trips")).as("M31.3 baseline; never reset an imported window").isEqualTo(21);
        var legacy=M31FleetIT.snapshot(jdbc);
        var dry=schedules.dryRun(anchor,14,true);
        assertThat(dry.planned()).isEqualTo(364); assertThat(dry.skipped()).isZero();
        assertThat(M31FleetIT.snapshot(jdbc)).isEqualTo(legacy);
        var applied=schedules.apply(anchor,14,true);
        writeReceipt("target/m31-4-import-receipt.json",Map.of("before",before,"dry",dry,"applied",applied,"after",counts()));
        assertThat(applied.created()).isEqualTo(364); assertThat(applied.skipped()).isZero();
        var imported=M31FleetIT.snapshot(jdbc);
        for(var t:legacy.keySet()) assertThat(imported.get(t)).as("legacy "+t).containsAll(legacy.get(t));
        assertThat(counts().get("operator_routes")).isEqualTo(29);
        assertThat(counts().get("operator_route_fares")).isEqualTo(34);
        assertThat(counts().get("trip_seat_segment_inventory")).isEqualTo(13090);
        var reused=schedules.apply(anchor,14,true);
        assertThat(reused.reused()).isEqualTo(364); assertThat(reused.created()).isZero();
        assertThat(M31FleetIT.snapshot(jdbc)).isEqualTo(imported);

        var locations=new HashMap<String,Long>();
        for(var p:M31Catalogue.places(M31Catalogue.Batch.FULL)) locations.put(p.key(),jdbc.queryForObject("SELECT id FROM locations WHERE name=?",Long.class,p.name()));
        var coverage=new ArrayList<Map<String,Object>>();
        for(var spec:M31Catalogue.routes(M31Catalogue.Batch.FULL)) {
            var response=get(search(locations.get(spec.from()),locations.get(spec.to()),anchor),null);
            int expected=spec.key().equals("HCM-DALAT")||spec.key().equals("DALAT-HCM")?2:1;
            assertThat(response.path("pagination").path("totalElements").asInt()).as(spec.key()).isEqualTo(expected);
            for(var row:response.path("data")) {
                var leg=applied.batches().stream().flatMap(b->b.departures().stream()).filter(d->d.tripId()==row.path("tripId").asLong()).findFirst().orElseThrow();
                assertThat(OffsetDateTime.parse(row.path("pickup").path("departureTime").asText()).toInstant()).isEqualTo(leg.leg().departure().atZone(BusGoTime.BUSINESS_ZONE).toInstant());
                assertThat(OffsetDateTime.parse(row.path("dropoff").path("arrivalTime").asText()).toInstant()).isEqualTo(leg.leg().arrival().atZone(BusGoTime.BUSINESS_ZONE).toInstant());
                assertThat(row.path("price").decimalValue()).isEqualByComparingTo(leg.grossFare());
                assertThat(row.path("availableSeats").asInt()).isEqualTo(leg.seats());
            }
            coverage.add(Map.of("route",spec.key(),"results",expected));
        }
        String popular=search(locations.get("HCM"),locations.get("DALAT"),anchor);
        var page0=get(popular+"&sort=PRICE_ASC&size=1",null);
        var page1=get(popular+"&sort=PRICE_ASC&size=1&page=1",null);
        assertThat(page0.path("data").get(0).path("price").decimalValue()).isEqualByComparingTo("250000");
        assertThat(page1.path("data").get(0).path("price").decimalValue()).isEqualByComparingTo("300000");
        assertThat(get(popular+"&minPrice=260000",null).path("pagination").path("totalElements").asInt()).isEqualTo(1);
        assertThat(get(popular+"&departureFrom=07:00",null).path("pagination").path("totalElements").asInt()).isEqualTo(1);
        assertThat(get(search(locations.get("HCM"),locations.get("DALAT"),LocalDate.now(BusGoTime.BUSINESS_ZONE).minusDays(1)),null).path("data").size()).isZero();
        var overnight=get(search(locations.get("NHATRANG"),locations.get("HCM"),anchor.plusDays(13)),null).path("data").get(0);
        assertThat(OffsetDateTime.parse(overnight.path("dropoff").path("arrivalTime").asText()).atZoneSameInstant(BusGoTime.BUSINESS_ZONE).toLocalDate()).isEqualTo(anchor.plusDays(14));

        var found=get(search(locations.get("HCM"),locations.get("PHANTHIET"),anchor),null).path("data").get(0);
        long trip=found.path("tripId").asLong(),owner=found.path("operator").path("id").asLong();
        BigDecimalHolder amount=new BigDecimalHolder(found.path("price").decimalValue());
        String journey="?pickupLocationId="+locations.get("HCM")+"&dropoffLocationId="+locations.get("PHANTHIET");
        var detail=get("/api/v1/trips/"+trip+journey,null).path("data");
        assertThat(detail.path("price").decimalValue()).isEqualByComparingTo(amount.value);
        var seatMap=get("/api/v1/trips/"+trip+"/seats"+journey,null).path("data");
        assertThat(seatMap.path("availableSeatCount").asInt()).isEqualTo(40);
        long seat=seatMap.path("seats").get(0).path("tripSeatId").asLong(); String code=seatMap.path("seats").get(0).path("seatCode").asText();
        String email="m31-schedule-"+UUID.randomUUID()+"@demo.example",password=UUID.randomUUID().toString();
        post("/api/v1/auth/register",Map.of("fullName","Khách M31 demo","email",email,"phone","DEMO-CUSTOMER","password",password),null,201);
        String token=post("/api/v1/auth/login",Map.of("email",email,"password",password),null,200).path("data").path("accessToken").asText();
        var holdBody=Map.of("tripId",trip,"pickupLocationId",locations.get("HCM"),"dropoffLocationId",locations.get("PHANTHIET"),"tripSeatIds",List.of(seat));
        var hold=post("/api/v1/seat-holds",holdBody,token,201).path("data"); String holdToken=hold.path("holdToken").asText();
        assertThat(hold.path("totalPrice").decimalValue()).isEqualByComparingTo(amount.value);
        // No campaign exists in M31.4: normal UI base quote is the hold plus voucher-options gross.
        var quote=post("/api/v1/bookings/voucher-options",Map.of("holdToken",holdToken),token,200).path("data");
        assertThat(quote.path("grossAmount").decimalValue()).isEqualByComparingTo(amount.value);
        assertThat(quote.path("options").size()).isZero();
        var booking=post("/api/v1/bookings",Map.of("holdToken",holdToken,"contactName","Khách M31 demo","contactPhone","DEMO-CUSTOMER","contactEmail",email),token,201).path("data");
        long bookingId=booking.path("bookingId").asLong();
        assertThat(booking.path("tripId").asLong()).isEqualTo(trip);
        assertThat(booking.path("price").path("grossAmount").decimalValue()).isEqualByComparingTo(amount.value);
        var payment=post("/api/v1/bookings/"+bookingId+"/payments/mock-confirm",Map.of(),token,200).path("data");
        assertThat(payment.path("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(payment.path("amount").decimalValue()).isEqualByComparingTo(amount.value);
        var again=post("/api/v1/bookings/"+bookingId+"/payments/mock-confirm",Map.of(),token,200).path("data");
        assertThat(again.path("paymentId").asLong()).isEqualTo(payment.path("paymentId").asLong());
        var ticket=get("/api/v1/bookings/"+bookingId+"/ticket",token).path("data");
        assertThat(ticket.path("tripId").asLong()).isEqualTo(trip); assertThat(ticket.path("operator").path("id").asLong()).isEqualTo(owner);
        assertThat(ticket.path("tickets").size()).isEqualTo(1);
        assertThat(ticket.path("tickets").get(0).path("seatCode").asText()).isEqualTo(code);
        assertThat(ticket.path("tickets").get(0).path("status").asText()).isEqualTo("VALID");
        assertThat(ticket.path("tickets").get(0).path("qrData").asText()).isNotBlank();
        post("/api/v1/seat-holds",holdBody,token,409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE booking_id=?",Long.class,bookingId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory WHERE trip_seat_id=? AND status='BOOKED'",Long.class,seat)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory WHERE hold_token=?",Long.class,holdToken)).isZero();
        var paidRows=M31FleetIT.snapshot(jdbc); var paidRerun=schedules.apply(anchor,14,true);
        assertThat(paidRerun.reused()).isEqualTo(364); assertThat(M31FleetIT.snapshot(jdbc)).isEqualTo(paidRows);
        writeReceipt("target/m31-4-acceptance.json",Map.of("anchor",anchor,"horizon",14,"before",before,"after",counts(),"apply",applied,"rerun",paidRerun,
            "coverage",coverage,"bookingSmoke",Map.of("bookingId",bookingId,"tripId",trip,"operatorId",owner,"seatId",seat,"amount",amount.value,"paid",true,"validQrPresent",true,"bookedInventory",1,"doubleBookingRejected",true)));
    }
    private record BigDecimalHolder(java.math.BigDecimal value) {}
    private String search(long from,long to,LocalDate date) { return "/api/v1/trips/search?pickupLocationId="+from+"&dropoffLocationId="+to+"&departureDate="+date; }
    private JsonNode get(String path,String token) throws Exception { return request(path,HttpMethod.GET,null,token,200); }
    private JsonNode post(String path,Object body,String token,int status) throws Exception { return request(path,HttpMethod.POST,body,token,status); }
    private JsonNode request(String path,HttpMethod method,Object body,String token,int expected) throws Exception {
        var headers=new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        if(token!=null) headers.setBearerAuth(token);
        var response=http.exchange(path,method,new HttpEntity<>(body,headers),String.class);
        assertThat(response.getStatusCode().value()).as(method+" "+path).isEqualTo(expected);
        return json.readTree(response.getBody());
    }
    private void writeReceipt(String file,Object receipt) throws Exception { Files.writeString(Path.of(file),json.writerWithDefaultPrettyPrinter().writeValueAsString(receipt)); }
    private Map<String,Long> counts() {
        var result=new TreeMap<String,Long>();
        for(var t:List.of("transport_operators","buses","bus_types","seat_templates","locations","routes","route_stops","operator_routes","operator_route_fares",
                "trips","trip_stops","trip_segments","trip_seats","trip_seat_segment_inventory","users","bookings","payments","tickets","operator_media"))
            result.put(t,jdbc.queryForObject("SELECT COUNT(*) FROM "+t,Long.class));
        return result;
    }
}
