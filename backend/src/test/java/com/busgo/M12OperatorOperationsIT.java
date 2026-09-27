package com.busgo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.security.CurrentUser;
import com.busgo.operator.entity.*;
import com.busgo.operator.repository.OperatorStaffRepository;
import com.busgo.user.entity.*;
import com.busgo.user.repository.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class M12OperatorOperationsIT extends M8BookingTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserRoleRepository userRoles;
    @Autowired OperatorStaffRepository staff;

    @Test
    void operatorAuthorizationIsolationListFiltersAndDetailRelationships() throws Exception {
        Fixture owned = fixture();
        Fixture foreign = fixture();
        String admin = adminToken(owned);
        String foreignAdmin = adminToken(foreign);
        UserAuth customer = customer("m12-list");
        long bookingId = createBooking(customer,
                hold(customer, owned, 0, 2, 0).holdToken());
        // 17:30 UTC is 00:30 on the following Vietnam business date.
        jdbc.update("UPDATE bookings SET created_at=? WHERE id=?",
                LocalDateTime.of(2026, 1, 1, 17, 30), bookingId);

        mvc.perform(get("/api/v1/operator/bookings")
                        .header("Authorization", bearer(customer.token())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/operator/bookings")
                        .header("Authorization", bearer(admin))
                        .param("q", "nguyen")
                        .param("tripId", owned.trip().getId().toString())
                        .param("status", "PENDING")
                        .param("paymentStatus", "PENDING")
                        .param("date", "2026-01-02")
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.length()").value(1))
                .andExpect(jsonPath("data[0].bookingId").value(bookingId))
                .andExpect(jsonPath("data[0].paymentStatus").value("PENDING"))
                .andExpect(jsonPath("pagination.size").value(1));
        mvc.perform(get("/api/v1/operator/bookings")
                        .header("Authorization", bearer(admin)).param("date", "2026-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.length()").value(0));

        mvc.perform(get("/api/v1/operator/bookings/{id}", bookingId)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.items[0].passengerName").doesNotExist())
                .andExpect(jsonPath("data.items[0].ticket").doesNotExist())
                .andExpect(jsonPath("data.payments.length()").value(0));
        mvc.perform(get("/api/v1/operator/bookings/{id}", bookingId)
                        .header("Authorization", bearer(foreignAdmin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("BOOKING_NOT_FOUND"));

        confirm(customer, bookingId).andExpect(status().isOk());
        mvc.perform(get("/api/v1/operator/bookings/{id}", bookingId)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.items[0].passengerName").doesNotExist())
                .andExpect(jsonPath("data.items[0].ticket.passengerName").value("Nguyen Giang"))
                .andExpect(jsonPath("data.items[0].ticket.paymentId").isNumber())
                .andExpect(jsonPath("data.payments[0].status").value("PAID"));
    }

    @Test
    void manifestUsesConfirmedItemsAndAllowsSeatReuseAcrossSegments() throws Exception {
        Fixture fixture = fixture();
        String admin = adminToken(fixture);
        UserAuth first = customer("m12-manifest-a");
        UserAuth second = customer("m12-manifest-b");
        UserAuth pending = customer("m12-manifest-p");
        long firstBooking = createBooking(first, hold(first, fixture, 0, 2, 0, 1).holdToken());
        long secondBooking = createBooking(second, hold(second, fixture, 2, 3, 0).holdToken());
        createBooking(pending, hold(pending, fixture, 2, 3, 1).holdToken());
        confirm(first, firstBooking).andExpect(status().isOk());
        confirm(second, secondBooking).andExpect(status().isOk());

        mvc.perform(get("/api/v1/operator/trips/{id}/passengers", fixture.trip().getId())
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.passengers.length()").value(3))
                .andExpect(jsonPath("data.passengers[0].tripSeatId")
                        .value(fixture.seats().get(0).getId()))
                .andExpect(jsonPath("data.passengers[2].tripSeatId")
                        .value(fixture.seats().get(0).getId()))
                .andExpect(jsonPath("data.passengers[0].passengerName").doesNotExist())
                .andExpect(jsonPath("data.passengers[0].ticketPassengerName")
                        .value("Nguyen Giang"));
    }

    @Test
    void occupancyIsSegmentAwareAndShowsAllInventoryStates() throws Exception {
        Fixture fixture = fixture();
        String admin = adminToken(fixture);
        UserAuth pendingOwner = customer("m12-occ-pending");
        UserAuth confirmedOwner = customer("m12-occ-confirmed");
        UserAuth heldOwner = customer("m12-occ-held");
        long pending = createBooking(pendingOwner,
                hold(pendingOwner, fixture, 0, 1, 0).holdToken());
        long confirmed = createBooking(confirmedOwner,
                hold(confirmedOwner, fixture, 1, 2, 0).holdToken());
        confirm(confirmedOwner, confirmed).andExpect(status().isOk());
        hold(heldOwner, fixture, 1, 2, 1);
        jdbc.update("""
                UPDATE trip_seat_segment_inventory SET status='BLOCKED', version=version+1
                WHERE trip_seat_id=? AND trip_segment_id=?
                """, fixture.seats().get(1).getId(), fixture.segments().get(2).getId());

        mvc.perform(get("/api/v1/operator/trips/{id}/occupancy", fixture.trip().getId())
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.seatCount").value(2))
                .andExpect(jsonPath("data.segmentCount").value(3))
                .andExpect(jsonPath("data.wholeTripAvailableSeatCount").value(0))
                .andExpect(jsonPath("data.segments[0].counts.booked").value(1))
                .andExpect(jsonPath("data.segments[1].counts.booked").value(1))
                .andExpect(jsonPath("data.segments[1].counts.held").value(1))
                .andExpect(jsonPath("data.segments[2].counts.blocked").value(1))
                .andExpect(jsonPath("data.seats[0].segments[0].bookingId").value(pending))
                .andExpect(jsonPath("data.seats[0].segments[0].bookingStatus").value("PENDING"))
                .andExpect(jsonPath("data.seats[0].segments[2].status").value("AVAILABLE"));
    }

    @Test
    void tripStatusTransitionsAreForwardOnlyIdempotentAndOwned() throws Exception {
        Fixture owned = fixture();
        Fixture foreign = fixture();
        String admin = adminToken(owned);
        String foreignAdmin = adminToken(foreign);
        long tripId = owned.trip().getId();

        mvc.perform(get("/api/v1/operator/trips/{id}/passengers", tripId)
                        .header("Authorization", bearer(foreignAdmin)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/operator/trips/{id}/occupancy", tripId)
                        .header("Authorization", bearer(foreignAdmin)))
                .andExpect(status().isNotFound());
        patchStatus(foreignAdmin, tripId, "BOARDING").andExpect(status().isNotFound());
        patchStatus(admin, tripId, "DEPARTED").andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("INVALID_TRIP_STATUS_TRANSITION"));
        patchStatus(admin, tripId, "BOARDING").andExpect(status().isOk());
        patchStatus(admin, tripId, "BOARDING").andExpect(status().isOk());
        patchStatus(admin, tripId, "SCHEDULED").andExpect(status().isConflict());
        patchStatus(admin, tripId, "CANCELLED").andExpect(status().isConflict());
        patchStatus(admin, tripId, "DEPARTED").andExpect(status().isOk());
        patchStatus(admin, tripId, "COMPLETED").andExpect(status().isOk());
        patchStatus(admin, tripId, "DEPARTED").andExpect(status().isConflict());
    }

    @Test
    void paymentWindowClosesAtDepartureButExistingSuccessRemainsIdempotent() throws Exception {
        Fixture fixture = fixture();
        String admin = adminToken(fixture);
        UserAuth before = customer("m12-pay-before");
        long paid = createBooking(before, hold(before, fixture, 0, 1, 0).holdToken());
        confirm(before, paid).andExpect(status().isOk());
        patchStatus(admin, fixture.trip().getId(), "BOARDING").andExpect(status().isOk());
        patchStatus(admin, fixture.trip().getId(), "DEPARTED").andExpect(status().isOk());
        confirm(before, paid).andExpect(status().isOk());

        Fixture departed = fixture();
        String departedAdmin = adminToken(departed);
        UserAuth after = customer("m12-pay-after");
        long unpaid = createBooking(after, hold(after, departed, 0, 1, 0).holdToken());
        patchStatus(departedAdmin, departed.trip().getId(), "BOARDING").andExpect(status().isOk());
        patchStatus(departedAdmin, departed.trip().getId(), "DEPARTED").andExpect(status().isOk());
        confirm(after, unpaid).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("PAYMENT_WINDOW_CLOSED"));
    }

    private long createBooking(UserAuth owner, String token) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/bookings")
                        .header("Authorization", bearer(owner.token()))
                        .contentType("application/json").content(bookingBody(token)))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.bookingId")).longValue();
    }

    private ResultActions confirm(UserAuth owner, long bookingId) throws Exception {
        return mvc.perform(post("/api/v1/bookings/{id}/payments/mock-confirm", bookingId)
                .header("Authorization", bearer(owner.token())));
    }

    private ResultActions patchStatus(String token, long tripId, String status) throws Exception {
        return mvc.perform(patch("/api/v1/operator/trips/{id}/status", tripId)
                .header("Authorization", bearer(token)).contentType("application/json")
                .content("{\"status\":\"" + status + "\"}"));
    }

    private String adminToken(Fixture fixture) {
        User user = new User();
        user.setFullName("M12 operator admin");
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPhone("09" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        user.setPasswordHash("not-used");
        user.setStatus(UserStatus.ACTIVE);
        users.saveAndFlush(user);
        userRoles.saveAndFlush(new UserRole(user,
                roles.findByCode(RoleCode.OPERATOR_ADMIN).orElseThrow()));
        OperatorStaff membership = new OperatorStaff();
        membership.setOperator(fixture.operator()); membership.setUser(user);
        membership.setStaffCode(UUID.randomUUID().toString());
        membership.setStatus(ActiveStatus.ACTIVE); staff.saveAndFlush(membership);
        CurrentUser current = new CurrentUser(user.getId(), java.util.List.of(RoleCode.OPERATOR_ADMIN));
        return jwt.issue(current, "access");
    }

    private static String bearer(String token) { return "Bearer " + token; }
}
