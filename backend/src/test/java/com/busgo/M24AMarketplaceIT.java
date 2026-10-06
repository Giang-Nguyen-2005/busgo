package com.busgo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.busgo.trip.entity.TripStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class M24AMarketplaceIT extends M8BookingTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.busgo.user.repository.UserRepository users;
    @Autowired com.busgo.user.repository.RoleRepository roles;
    @Autowired com.busgo.user.repository.UserRoleRepository userRoles;
    @Autowired com.busgo.operator.repository.OperatorStaffRepository staff;

    UserAuth actor(Fixture f, com.busgo.user.entity.RoleCode role) {
        var original=customer("M24-actor");
        var entity=users.findById(original.user().id()).orElseThrow();
        var assignment=new com.busgo.user.entity.UserRole(entity,roles.findByCode(role).orElseThrow()); userRoles.saveAndFlush(assignment);
        var member=new com.busgo.operator.entity.OperatorStaff(); member.setUser(entity); member.setOperator(f.operator());
        member.setStaffCode(UUID.randomUUID().toString()); member.setStatus(com.busgo.common.entity.ActiveStatus.ACTIVE); staff.saveAndFlush(member);
        var current=new com.busgo.common.security.CurrentUser(entity.getId(),java.util.List.of(com.busgo.user.entity.RoleCode.CUSTOMER,role));
        return new UserAuth(current,jwt.issue(current,"access"));
    }

    long booking(Fixture f, UserAuth user, String status, boolean cancelled) {
        String code = "M24-" + UUID.randomUUID();
        jdbc.update("""
            INSERT INTO bookings(booking_code,customer_id,trip_id,pickup_trip_stop_id,
            dropoff_trip_stop_id,contact_name,contact_phone,contact_email,total_amount,status,created_at,updated_at)
            VALUES(?,?,?,?,?,'Demo customer','0900000000','demo@example.test',300,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            """, code, user.user().id(), f.trip().getId(), f.stops().get(0).getId(), f.stops().get(3).getId(), status);
        long id = jdbc.queryForObject("SELECT id FROM bookings WHERE booking_code=?", Long.class, code);
        jdbc.update("INSERT INTO booking_items(booking_id,trip_seat_id,seat_code,unit_price,cancelled,created_at) VALUES(?,?,'A01',300,?,UTC_TIMESTAMP(6))",
                id, f.seats().get(0).getId(), cancelled);
        return id;
    }
    void complete(Fixture f) { f.trip().setStatus(TripStatus.COMPLETED); trips.saveAndFlush(f.trip()); }
    String body(int rating) { return "{\"rating\":" + rating + ",\"text\":\"Chuyến xe đúng giờ\"}"; }

    @Test void completedBookingCanCreateEditDeleteAndAggregatesFollow() throws Exception {
        var f=fixture(); var u=customer("M24"); complete(f); long id=booking(f,u,"CONFIRMED",false);
        mvc.perform(get("/api/v1/bookings/"+id+"/review-eligibility").header("Authorization","Bearer "+u.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("data.eligible").value(true));
        mvc.perform(post("/api/v1/bookings/"+id+"/review").header("Authorization","Bearer "+u.token()).contentType(MediaType.APPLICATION_JSON).content(body(5)))
            .andExpect(status().isCreated());
        profile(f,5,1);
        mvc.perform(put("/api/v1/bookings/"+id+"/review").header("Authorization","Bearer "+u.token()).contentType(MediaType.APPLICATION_JSON).content(body(3)))
            .andExpect(status().isOk());
        profile(f,3,1);
        mvc.perform(delete("/api/v1/bookings/"+id+"/review").header("Authorization","Bearer "+u.token())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/operators/"+f.operator().getId())).andExpect(status().isOk())
            .andExpect(jsonPath("data.reviewCount").value(0)).andExpect(jsonPath("data.averageRating").isEmpty());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM marketplace_reviews WHERE booking_id=? AND deleted_at IS NOT NULL", Long.class,id)).isEqualTo(1);
    }
    void profile(Fixture f,int rating,int count) throws Exception {
        mvc.perform(get("/api/v1/public/operators/"+f.operator().getId())).andExpect(status().isOk())
            .andExpect(jsonPath("data.averageRating").value(rating)).andExpect(jsonPath("data.reviewCount").value(count));
    }
    @ParameterizedTest @ValueSource(strings={"PENDING","CANCELLED"})
    void unretainedBookingCannotReview(String state) throws Exception {
        var f=fixture(); var u=customer("M24"); complete(f); long id=booking(f,u,state,false);
        mvc.perform(post("/api/v1/bookings/"+id+"/review").header("Authorization","Bearer "+u.token()).contentType(MediaType.APPLICATION_JSON).content(body(5))).andExpect(status().isConflict());
    }
    @Test void nonCompletedCannotReview() throws Exception {
        var f=fixture(); var u=customer("M24"); long id=booking(f,u,"CONFIRMED",false);
        mvc.perform(post("/api/v1/bookings/"+id+"/review").header("Authorization","Bearer "+u.token()).contentType(MediaType.APPLICATION_JSON).content(body(5))).andExpect(status().isConflict());
    }
    @Test void fullyCancelledItemsCannotReview() throws Exception {
        var f=fixture(); var u=customer("M24"); complete(f); long id=booking(f,u,"CONFIRMED",true);
        mvc.perform(post("/api/v1/bookings/"+id+"/review").header("Authorization","Bearer "+u.token()).contentType(MediaType.APPLICATION_JSON).content(body(5))).andExpect(status().isConflict());
    }
    @Test void partialCancellationCanReview() throws Exception {
        var f=fixture(); var u=customer("M24"); complete(f); long id=booking(f,u,"COMPLETED",true);
        jdbc.update("INSERT INTO booking_items(booking_id,trip_seat_id,seat_code,unit_price,cancelled,created_at) VALUES(?,?,'A02',300,false,UTC_TIMESTAMP(6))",id,f.seats().get(1).getId());
        mvc.perform(post("/api/v1/bookings/"+id+"/review").header("Authorization","Bearer "+u.token()).contentType(MediaType.APPLICATION_JSON).content(body(5))).andExpect(status().isCreated());
    }
    @ParameterizedTest @ValueSource(ints={0,6,-1})
    void ratingRangeValidated(int rating) throws Exception {
        var f=fixture(); var u=customer("M24"); complete(f); long id=booking(f,u,"CONFIRMED",false);
        mvc.perform(post("/api/v1/bookings/"+id+"/review").header("Authorization","Bearer "+u.token()).contentType(MediaType.APPLICATION_JSON).content(body(rating))).andExpect(status().isBadRequest());
    }
    @Test void foreignCustomerConcealedForAllActions() throws Exception {
        var f=fixture(); var owner=customer("M24"); var foreign=customer("Foreign"); complete(f); long id=booking(f,owner,"CONFIRMED",false);
        for (var request : java.util.List.of(get("/api/v1/bookings/"+id+"/review-eligibility"),post("/api/v1/bookings/"+id+"/review"),put("/api/v1/bookings/"+id+"/review"),delete("/api/v1/bookings/"+id+"/review"))) {
            mvc.perform(request.header("Authorization","Bearer "+foreign.token()).contentType(MediaType.APPLICATION_JSON).content(body(5))).andExpect(status().isNotFound());
        }
    }
    @Test void duplicateCreateRejected() throws Exception {
        var f=fixture(); var u=customer("M24"); complete(f); long id=booking(f,u,"CONFIRMED",false);
        for(int i=0;i<2;i++) mvc.perform(post("/api/v1/bookings/"+id+"/review").header("Authorization","Bearer "+u.token()).contentType(MediaType.APPLICATION_JSON).content(body(5))).andExpect(status().is(i==0?201:409));
    }
    @Test void anonymousCannotMutate() throws Exception {
        mvc.perform(post("/api/v1/bookings/1/review").contentType(MediaType.APPLICATION_JSON).content(body(5))).andExpect(status().isUnauthorized());
    }
    @Test void operatorAdminRespondsButCannotChangeCustomerReview() throws Exception {
        var f=fixture(); var u=customer("M24"); var admin=actor(f,com.busgo.user.entity.RoleCode.OPERATOR_ADMIN); complete(f);
        long id=booking(f,u,"CONFIRMED",false);
        mvc.perform(post("/api/v1/bookings/"+id+"/review").header("Authorization","Bearer "+u.token()).contentType(MediaType.APPLICATION_JSON).content(body(5))).andExpect(status().isCreated());
        long reviewId=jdbc.queryForObject("SELECT id FROM marketplace_reviews WHERE booking_id=?",Long.class,id);
        mvc.perform(put("/api/v1/operator/reviews/"+reviewId+"/response").header("Authorization","Bearer "+admin.token()).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Cảm ơn bạn\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/operators/"+f.operator().getId()+"/reviews")).andExpect(status().isOk()).andExpect(jsonPath("data[0].response").value("Cảm ơn bạn"));
        mvc.perform(put("/api/v1/bookings/"+id+"/review").header("Authorization","Bearer "+admin.token()).contentType(MediaType.APPLICATION_JSON).content(body(1))).andExpect(status().isNotFound());
        profile(f,5,1);
    }
    @ParameterizedTest @ValueSource(strings={"OPERATOR_STAFF","SYSTEM_ADMIN"})
    void staffAndSystemAdminCannotMutate(String role) throws Exception {
        var f=fixture(); var a=actor(f,com.busgo.user.entity.RoleCode.valueOf(role));
        mvc.perform(put("/api/v1/operator/reviews/1/response").header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Response\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/operator/marketplace-profile").header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"Profile\"}")).andExpect(status().isForbidden());
    }
    @Test void foreignOperatorConcealedAndStaffReads() throws Exception {
        var f=fixture(); var other=fixture(); var u=customer("M24"); var foreign=actor(other,com.busgo.user.entity.RoleCode.OPERATOR_ADMIN); var reader=actor(f,com.busgo.user.entity.RoleCode.OPERATOR_STAFF); complete(f);
        long id=booking(f,u,"CONFIRMED",false);
        mvc.perform(post("/api/v1/bookings/"+id+"/review").header("Authorization","Bearer "+u.token()).contentType(MediaType.APPLICATION_JSON).content(body(5))).andExpect(status().isCreated());
        long reviewId=jdbc.queryForObject("SELECT id FROM marketplace_reviews WHERE booking_id=?",Long.class,id);
        mvc.perform(put("/api/v1/operator/reviews/"+reviewId+"/response").header("Authorization","Bearer "+foreign.token()).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Response\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/operator/reviews").header("Authorization","Bearer "+reader.token())).andExpect(status().isOk()).andExpect(jsonPath("data.length()").value(1));
    }
    @Test void profileMetadataIsPublicButPrivateFieldsAreNot() throws Exception {
        var f=fixture(); var a=actor(f,com.busgo.user.entity.RoleCode.OPERATOR_ADMIN);
        mvc.perform(put("/api/v1/operator/marketplace-profile").header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"Nhà xe demo\",\"logoUrl\":\"/images/busgo/bus-standard.jpg\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/operators/"+f.operator().getId())).andExpect(status().isOk()).andExpect(jsonPath("data.description").value("Nhà xe demo")).andExpect(jsonPath("data.phone").doesNotExist()).andExpect(jsonPath("data.code").doesNotExist());
        f.operator().setStatus(com.busgo.operator.entity.OperatorStatus.INACTIVE); operators.saveAndFlush(f.operator());
        mvc.perform(get("/api/v1/public/operators/"+f.operator().getId())).andExpect(status().isNotFound());
    }
    @Test void ratingAverageAndDeletedExclusionAreDatabaseDerived() throws Exception {
        var f=fixture(); complete(f);
        for(int rating:java.util.List.of(2,4)) {
            var u=customer("M24"); long id=booking(f,u,"CONFIRMED",false);
            mvc.perform(post("/api/v1/bookings/"+id+"/review").header("Authorization","Bearer "+u.token()).contentType(MediaType.APPLICATION_JSON).content(body(rating))).andExpect(status().isCreated());
        }
        profile(f,3,2);
    }
    @Test void searchRatingSeatsAndBounds() throws Exception {
        var f=fixture(); var u=customer("M24"); long id=booking(f,u,"CONFIRMED",false);
        jdbc.update("INSERT INTO marketplace_reviews(booking_id,rating,review_text,created_at,updated_at) VALUES(?,4,'Test fixture',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",id);
        String path="/api/v1/trips/search";
        var base=java.util.Map.of("pickupLocationId",f.locations().get(0).getId().toString(),"dropoffLocationId",f.locations().get(3).getId().toString(),"departureDate","2030-09-20");
        for(String sort:java.util.List.of("RECOMMENDED","DEPARTURE_ASC","DEPARTURE_DESC","PRICE_ASC","PRICE_DESC","RATING_DESC")) {
            var request=get(path).param("sort",sort).param("minRating","4").param("minSeats","2");base.forEach(request::param);
            mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("data[0].availableSeats").value(2)).andExpect(jsonPath("data[0].operator.averageRating").value(4));
        }
        for(var filter:java.util.List.of(java.util.Map.entry("minRating","5"),java.util.Map.entry("minSeats","3"),java.util.Map.entry("operatorId","999999"),java.util.Map.entry("busTypeId","999999"),java.util.Map.entry("maxPrice","200"),java.util.Map.entry("departureFrom","09:00"))) {
            var request=get(path).param(filter.getKey(),filter.getValue());base.forEach(request::param);
            mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("data.length()").value(0));
        }
        mvc.perform(get("/api/v1/public/operators").param("size","101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/public/reviews/recent").param("size","101")).andExpect(status().isBadRequest());
    }
    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void simultaneousCreateProducesExactlyOneVisibleReview() throws Exception {
        var f=fixture(); var u=customer("M24-race"); complete(f); long id=booking(f,u,"CONFIRMED",false);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2); var start=new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Integer> request=()->{start.await();return mvc.perform(post("/api/v1/bookings/"+id+"/review").header("Authorization","Bearer "+u.token()).contentType(MediaType.APPLICATION_JSON).content(body(5))).andReturn().getResponse().getStatus();};
            var first=pool.submit(request);var second=pool.submit(request);start.countDown();
            assertThat(java.util.List.of(first.get(15,java.util.concurrent.TimeUnit.SECONDS),second.get(15,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM marketplace_reviews WHERE booking_id=? AND deleted_at IS NULL",Long.class,id)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
            jdbc.update("DELETE FROM marketplace_reviews WHERE booking_id=?",id);
            jdbc.update("DELETE FROM booking_items WHERE booking_id=?",id);
            jdbc.update("DELETE FROM bookings WHERE id=?",id);
            long trip=f.trip().getId();
            jdbc.update("DELETE FROM trip_seat_segment_inventory WHERE trip_seat_id IN (SELECT id FROM trip_seats WHERE trip_id=?)",trip);
            jdbc.update("DELETE FROM trip_seats WHERE trip_id=?",trip);
            jdbc.update("DELETE FROM trip_segments WHERE trip_id=?",trip);
            jdbc.update("DELETE FROM trip_stops WHERE trip_id=?",trip);
            jdbc.update("DELETE FROM trips WHERE id=?",trip);
            jdbc.update("DELETE FROM operator_route_fares WHERE operator_route_id=?",f.operatorRoute().getId());
            jdbc.update("DELETE FROM buses WHERE id=?",f.bus().getId());
            jdbc.update("DELETE FROM seat_templates WHERE bus_type_id=?",f.busType().getId());
            jdbc.update("DELETE FROM bus_types WHERE id=?",f.busType().getId());
            jdbc.update("DELETE FROM operator_routes WHERE id=?",f.operatorRoute().getId());
            jdbc.update("DELETE FROM route_stops WHERE route_id=?",f.route().getId());
            jdbc.update("DELETE FROM routes WHERE id=?",f.route().getId());
            jdbc.update("DELETE FROM transport_operators WHERE id=?",f.operator().getId());
            for(var location:f.locations()) jdbc.update("DELETE FROM locations WHERE id=?",location.getId());
            jdbc.update("DELETE FROM refresh_tokens WHERE user_id=?",u.user().id());
            jdbc.update("DELETE FROM user_roles WHERE user_id=?",u.user().id());
            jdbc.update("DELETE FROM users WHERE id=?",u.user().id());
        }
    }
}
