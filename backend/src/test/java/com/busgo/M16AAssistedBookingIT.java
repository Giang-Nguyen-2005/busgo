package com.busgo;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.busgo.booking.*;
import com.busgo.booking.AssistedBookingDtos.*;
import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.security.CurrentUser;
import com.busgo.operator.entity.*;
import com.busgo.operator.repository.OperatorStaffRepository;
import com.busgo.payment.entity.PaymentMethod;
import com.busgo.user.entity.*;
import com.busgo.user.repository.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@org.springframework.security.test.context.support.WithMockUser(roles = "OPERATOR_ADMIN")
class M16AAssistedBookingIT extends M16ATestSupport {
    @Autowired MockMvc mvc;

    @Test
    void offlineBookingReservesWithoutAccountPaymentOrTicketThenCollectsIdempotently() throws Exception {
        Fixture f = fixture(); CurrentUser admin = actor(f, RoleCode.OPERATOR_ADMIN);
        var b = assisted.create(admin, request(f, 0, 2, PaymentMethod.PAY_ON_BOARD));
        assertThat(jdbc.queryForObject("SELECT customer_id FROM bookings WHERE id=?", Long.class, b.bookingId())).isNull();
        assertThat(count("payments", b.bookingId())).isZero(); assertThat(count("tickets", b.bookingId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory WHERE status='BOOKED' AND booking_item_id IN (SELECT id FROM booking_items WHERE booking_id=?)", Integer.class, b.bookingId())).isEqualTo(2);
        mvc.perform(get("/api/v1/operator/bookings/{id}", b.bookingId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("data.customer").doesNotExist())
                .andExpect(jsonPath("data.source").value("PHONE"))
                .andExpect(jsonPath("data.paymentMethod").value("PAY_ON_BOARD"));
        authenticate(admin);
        var paid = assisted.record(admin, b.bookingId(), new RecordPayment(PaymentMethod.PAY_ON_BOARD, "Collected in cash"));
        assertThat(assisted.record(admin, b.bookingId(), new RecordPayment(PaymentMethod.PAY_ON_BOARD, null)).paymentId()).isEqualTo(paid.paymentId());
        assertThat(count("payments", b.bookingId())).isOne(); assertThat(count("tickets", b.bookingId())).isOne();
        assertThat(jdbc.queryForObject("SELECT collected_by_user_id FROM payments WHERE booking_id=?", Long.class, b.bookingId())).isEqualTo(admin.id());
        assertThat(jdbc.queryForObject("SELECT reference_note FROM payments WHERE booking_id=?", String.class, b.bookingId())).isEqualTo("Collected in cash");
        UserAuth other = customer("m16-other");
        mvc.perform(get("/api/v1/bookings/me").header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("data.length()").value(0));
        mvc.perform(get("/api/v1/bookings/{id}", b.bookingId()).header("Authorization", "Bearer " + other.token())).andExpect(status().isNotFound());
    }

    @Test
    void publicTokenIsOpaqueMinimalScopedRotatableAndPaidReuseIsIdempotent() throws Exception {
        Fixture f = fixture(); CurrentUser admin = actor(f, RoleCode.OPERATOR_ADMIN);
        var b = assisted.create(admin, request(f, 0, 2, PaymentMethod.QR_TRANSFER));
        String old = token(assisted.issueLink(admin, b.bookingId()));
        String token = token(assisted.issueLink(admin, b.bookingId()));
        assertThat(token).hasSize(43).isNotEqualTo(old);
        assertThat(jdbc.queryForObject("SELECT payment_token_hash FROM bookings WHERE id=?", String.class, b.bookingId())).hasSize(64).isNotEqualTo(token);
        mvc.perform(get("/api/v1/public/payments/{token}", old)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/payments/{token}", token)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("data.bookingCode").value(b.bookingCode()))
                .andExpect(jsonPath("data.contact").doesNotExist()).andExpect(jsonPath("data.bookingId").doesNotExist())
                .andExpect(jsonPath("data.customer").doesNotExist()).andExpect(jsonPath("data.mockPayment").value(true));
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/v1/public/payments/{token}/mock-confirm", token))
                .andExpect(status().isOk()).andExpect(jsonPath("data.status").value("CONFIRMED"));
        assertThat(count("payments", b.bookingId())).isOne(); assertThat(count("tickets", b.bookingId())).isOne();
        assertThat(jdbc.queryForObject("SELECT collected_by_user_id FROM payments WHERE booking_id=?", Long.class, b.bookingId())).isNull();
        mvc.perform(get("/api/v1/public/payments/{token}", b.bookingId())).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/public/payments/{token}/mock-confirm", "x".repeat(43))).andExpect(status().isNotFound());
    }

    @Test
    void foreignStaffCustomerSystemAdminAndInactiveOperatorCannotMutate() throws Exception {
        Fixture f = fixture(); Fixture foreign = fixture();
        CurrentUser admin = actor(f, RoleCode.OPERATOR_ADMIN), staff = actor(f, RoleCode.OPERATOR_STAFF), foreignAdmin = actor(foreign, RoleCode.OPERATOR_ADMIN);
        var b = assisted.create(admin, request(f, 0, 2, PaymentMethod.QR_TRANSFER));
        for (CurrentUser denied : List.of(staff, actor(f, RoleCode.SYSTEM_ADMIN), customer("m16-customer").user())) {
            mvc.perform(post("/api/v1/operator/bookings/{id}/payments", b.bookingId()).header("Authorization", bearer(denied))
                    .contentType("application/json").content("{\"method\":\"QR_TRANSFER\"}")).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/operator/bookings").header("Authorization", bearer(denied))
                    .contentType("application/json").content(body(f))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/operator/bookings/{id}/payments", b.bookingId()).header("Authorization", bearer(foreignAdmin))
                .contentType("application/json").content("{\"method\":\"QR_TRANSFER\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/operator/bookings/{id}/payment-link", b.bookingId()).header("Authorization", bearer(foreignAdmin))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/operator/bookings/{id}", b.bookingId()).header("Authorization", bearer(foreignAdmin))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/operator/bookings").header("Authorization", bearer(foreignAdmin))
                .contentType("application/json").content(body(f))).andExpect(status().isNotFound());
        authenticate(admin);
        String token = token(assisted.issueLink(admin, b.bookingId()));
        em.flush();
        jdbc.update("UPDATE transport_operators SET status='INACTIVE' WHERE id=?", f.operator().getId());
        em.clear();
        mvc.perform(post("/api/v1/operator/bookings/{id}/payments", b.bookingId()).header("Authorization", bearer(admin))
                .contentType("application/json").content("{\"method\":\"QR_TRANSFER\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/public/payments/{token}/mock-confirm", token)).andExpect(status().isConflict());
        assertThat(count("payments", b.bookingId())).isZero();
    }

    @Test
    void incompleteBookedInventoryCannotIssuePaymentOrTickets() throws Exception {
        Fixture f = fixture(); CurrentUser admin = actor(f, RoleCode.OPERATOR_ADMIN);
        var b = assisted.create(admin, request(f, 0, 2, PaymentMethod.QR_TRANSFER));
        String token = token(assisted.issueLink(admin, b.bookingId()));
        jdbc.update("DELETE FROM trip_seat_segment_inventory WHERE trip_seat_id=? AND trip_segment_id=?",
                f.seats().get(0).getId(), f.segments().get(1).getId());
        mvc.perform(post("/api/v1/public/payments/{token}/mock-confirm", token)).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("BOOKING_INVENTORY_INCONSISTENT"));
        mvc.perform(post("/api/v1/operator/bookings/{id}/payments", b.bookingId()).header("Authorization", bearer(admin))
                .contentType("application/json").content("{\"method\":\"QR_TRANSFER\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("BOOKING_INVENTORY_INCONSISTENT"));
        assertThat(count("payments", b.bookingId())).isZero(); assertThat(count("tickets", b.bookingId())).isZero();
    }

    @Test
    void manualCollectionDuringBoardingDoesNotSetPassengerBoardingState() {
        Fixture f = fixture(); CurrentUser admin = actor(f, RoleCode.OPERATOR_ADMIN);
        var b = assisted.create(admin, request(f, 0, 2, PaymentMethod.PAY_ON_BOARD));
        jdbc.update("UPDATE trips SET status='BOARDING' WHERE id=?", f.trip().getId());
        jdbc.update("UPDATE trip_stops SET planned_departure_time=UTC_TIMESTAMP(6) - INTERVAL 1 MINUTE WHERE id=?", f.stops().get(0).getId());
        em.clear();
        assisted.record(admin, b.bookingId(), new RecordPayment(PaymentMethod.PAY_ON_BOARD, null));
        assertThat(count("tickets", b.bookingId())).isOne();
        assertThat(jdbc.queryForObject("SELECT status FROM trips WHERE id=?", String.class, f.trip().getId())).isEqualTo("BOARDING");
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id=?", String.class, b.bookingId())).isEqualTo("CONFIRMED");
    }

    @Test
    void journeyReuseAndValidationRemainComplete() throws Exception {
        Fixture f = fixture(); CurrentUser admin = actor(f, RoleCode.OPERATOR_ADMIN);
        assisted.create(admin, request(f, 0, 2, PaymentMethod.PAY_ON_BOARD));
        assisted.create(admin, request(f, 2, 3, PaymentMethod.QR_TRANSFER));
        mvc.perform(post("/api/v1/operator/bookings").header("Authorization", bearer(admin))
                .contentType("application/json").content(body(f))).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("SEAT_NOT_AVAILABLE"));
        Fixture missing = fixture(); CurrentUser missingAdmin = actor(missing, RoleCode.OPERATOR_ADMIN);
        jdbc.update("DELETE FROM trip_seat_segment_inventory WHERE trip_seat_id=? AND trip_segment_id=?", missing.seats().get(0).getId(), missing.segments().get(1).getId());
        mvc.perform(post("/api/v1/operator/bookings").header("Authorization", bearer(missingAdmin))
                .contentType("application/json").content(body(missing))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE trip_id=?", Integer.class, missing.trip().getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory WHERE trip_seat_id=? AND status='HELD'", Integer.class, missing.seats().get(0).getId())).isZero();
        mvc.perform(post("/api/v1/operator/bookings").header("Authorization", bearer(admin))
                .contentType("application/json").content(body(f).replace("Caller", "   "))).andExpect(status().isBadRequest());
    }
}

abstract class M16ATestSupport extends M8BookingTestSupport {
    @Autowired AssistedBookingService assisted;
    @Autowired BookingService bookingService;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserRoleRepository userRoles;
    @Autowired OperatorStaffRepository staff;
    @Autowired jakarta.persistence.EntityManager em;

    void authenticate(CurrentUser actor) {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(actor, null,
                actor.roles().stream().map(r -> new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + r.name())).toList()));
    }
    CurrentUser actor(Fixture f, RoleCode code) {
        User u = new User(); u.setFullName("M16 actor"); u.setEmail(UUID.randomUUID() + "@example.test");
        u.setPhone("09" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        u.setPasswordHash("not-used"); u.setStatus(UserStatus.ACTIVE); users.saveAndFlush(u);
        userRoles.saveAndFlush(new UserRole(u, roles.findByCode(code).orElseThrow()));
        if (code != RoleCode.SYSTEM_ADMIN) {
            OperatorStaff s = new OperatorStaff(); s.setOperator(f.operator()); s.setUser(u);
            s.setStaffCode(UUID.randomUUID().toString()); s.setStatus(ActiveStatus.ACTIVE); staff.saveAndFlush(s);
        }
        return new CurrentUser(u.getId(), List.of(code));
    }
    CreateRequest request(Fixture f, int from, int to, PaymentMethod method) {
        return new CreateRequest(f.trip().getId(), f.locations().get(from).getId(), f.locations().get(to).getId(),
                List.of(f.seats().get(0).getId()), "Caller", "0901234567", null, method);
    }
    String body(Fixture f) { return """
        {"tripId":%d,"pickupLocationId":%d,"dropoffLocationId":%d,"tripSeatIds":[%d],
         "contactName":"Caller","contactPhone":"0901234567","paymentMethod":"PAY_ON_BOARD"}
        """.formatted(f.trip().getId(), f.locations().get(0).getId(), f.locations().get(2).getId(), f.seats().get(0).getId()); }
    String bearer(CurrentUser user) { return "Bearer " + jwt.issue(user, "access"); }
    String token(PaymentLink link) { return link.path().substring("/pay/".length()); }
    int count(String table, Long id) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE booking_id=?", Integer.class, id); }
}
