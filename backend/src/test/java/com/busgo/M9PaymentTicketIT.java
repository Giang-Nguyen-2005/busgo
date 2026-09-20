package com.busgo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.persistence.EntityManager;
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
class M9PaymentTicketIT extends M8BookingTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    void paymentAndTicketEndpointsRequireAuthentication() throws Exception {
        mvc.perform(post("/api/v1/bookings/1/payments/mock-confirm"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/bookings/1/ticket"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerPaysServerAmountAndReceivesOneStableTicketPerSeat() throws Exception {
        Fixture fixture = fixture();
        UserAuth owner = customer("m9-happy");
        long bookingId = createBooking(owner, hold(owner, fixture, 0, 2, 0, 1).holdToken());

        MvcResult paid = confirm(owner, bookingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.bookingId").value(bookingId))
                .andExpect(jsonPath("data.method").value("MOCK_QR"))
                .andExpect(jsonPath("data.amount").value(400.00))
                .andExpect(jsonPath("data.paymentStatus").value("PAID"))
                .andExpect(jsonPath("data.bookingStatus").value("CONFIRMED"))
                .andExpect(jsonPath("data.transactionReference").isNotEmpty())
                .andExpect(jsonPath("data.paidAt").isNotEmpty())
                .andReturn();
        Number paymentId = com.jayway.jsonpath.JsonPath.read(
                paid.getResponse().getContentAsString(), "$.data.paymentId");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE booking_id=?",
                Integer.class, bookingId)).isOne();
        assertThat(jdbc.queryForObject("SELECT amount FROM payments WHERE id=?",
                java.math.BigDecimal.class, paymentId.longValue()))
                .isEqualByComparingTo("400.00");
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id=?",
                String.class, bookingId)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking_status_history WHERE booking_id=?",
                Integer.class, bookingId)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE booking_id=?",
                Integer.class, bookingId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT ticket_code) FROM tickets WHERE booking_id=?",
                Integer.class, bookingId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM trip_seat_segment_inventory inventory
                JOIN booking_items item ON item.id=inventory.booking_item_id
                WHERE item.booking_id=? AND inventory.status='BOOKED'
                  AND inventory.hold_token IS NULL AND inventory.held_by_user_id IS NULL
                  AND inventory.hold_expires_at IS NULL
                """, Integer.class, bookingId)).isEqualTo(4);

        MvcResult firstRead = ticket(owner, bookingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.status").value("CONFIRMED"))
                .andExpect(jsonPath("data.paymentStatus").value("PAID"))
                .andExpect(jsonPath("data.operator.name").value(fixture.operator().getName()))
                .andExpect(jsonPath("data.route.name").value(fixture.route().getName()))
                .andExpect(jsonPath("data.tickets.length()").value(2))
                .andExpect(jsonPath("data.tickets[0].passengerName").value("Nguyen Giang"))
                .andExpect(jsonPath("data.tickets[0].seatCode").value("A01"))
                .andExpect(jsonPath("data.tickets[0].qrData").value(
                        org.hamcrest.Matchers.matchesPattern("TKT-[A-F0-9]{32}")))
                .andReturn();
        String firstQr = com.jayway.jsonpath.JsonPath.read(
                firstRead.getResponse().getContentAsString(), "$.data.tickets[0].qrData");
        ticket(owner, bookingId).andExpect(status().isOk())
                .andExpect(jsonPath("data.tickets[0].qrData").value(firstQr));
    }

    @Test
    void repeatedConfirmationReturnsExistingPaymentAndTickets() throws Exception {
        Fixture fixture = fixture();
        UserAuth owner = customer("m9-repeat");
        long bookingId = createBooking(owner, hold(owner, fixture, 0, 1, 0).holdToken());
        String firstBody = confirm(owner, bookingId).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        Number firstPayment = com.jayway.jsonpath.JsonPath.read(firstBody, "$.data.paymentId");
        String firstReference = com.jayway.jsonpath.JsonPath.read(
                firstBody, "$.data.transactionReference");
        confirm(owner, bookingId).andExpect(status().isOk())
                .andExpect(jsonPath("data.paymentId").value(firstPayment));

        long otherBooking = createBooking(owner, hold(owner, fixture, 1, 2, 1).holdToken());
        String otherReference = com.jayway.jsonpath.JsonPath.read(confirm(owner, otherBooking)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.data.transactionReference");
        assertThat(otherReference).isNotEqualTo(firstReference);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE booking_id=?",
                Integer.class, bookingId)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE booking_id=?",
                Integer.class, bookingId)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking_status_history WHERE booking_id=?",
                Integer.class, bookingId)).isOne();
    }

    @Test
    void foreignOwnerIsHiddenAndPendingTicketIsUnavailable() throws Exception {
        Fixture fixture = fixture();
        UserAuth owner = customer("m9-owner");
        UserAuth other = customer("m9-other");
        long bookingId = createBooking(owner, hold(owner, fixture, 0, 1, 0).holdToken());

        confirm(other, bookingId).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("BOOKING_NOT_FOUND"));
        ticket(other, bookingId).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("BOOKING_NOT_FOUND"));
        ticket(owner, bookingId).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("TICKET_NOT_AVAILABLE"));
    }

    @Test
    void cancelledAndCompletedBookingsCannotBePaid() throws Exception {
        Fixture fixture = fixture();
        UserAuth owner = customer("m9-state");
        long cancelled = createBooking(owner, hold(owner, fixture, 0, 1, 0).holdToken());
        long completed = createBooking(owner, hold(owner, fixture, 1, 2, 1).holdToken());
        jdbc.update("UPDATE bookings SET status='CANCELLED' WHERE id=?", cancelled);
        jdbc.update("UPDATE bookings SET status='COMPLETED' WHERE id=?", completed);
        entityManager.clear();

        confirm(owner, cancelled).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("BOOKING_NOT_PAYABLE"));
        confirm(owner, completed).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("BOOKING_NOT_PAYABLE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE booking_id IN (?,?)",
                Integer.class, cancelled, completed)).isZero();
    }

    @Test
    void inconsistentBookedInventoryCannotBeConfirmed() throws Exception {
        Fixture fixture = fixture();
        UserAuth owner = customer("m9-corrupt");
        long bookingId = createBooking(owner, hold(owner, fixture, 0, 2, 0).holdToken());
        Long inventoryId = jdbc.queryForObject("""
                SELECT MIN(inventory.id) FROM trip_seat_segment_inventory inventory
                JOIN booking_items item ON item.id=inventory.booking_item_id
                WHERE item.booking_id=?
                """, Long.class, bookingId);
        jdbc.update("UPDATE trip_seat_segment_inventory SET status='AVAILABLE' WHERE id=?",
                inventoryId);
        entityManager.clear();

        confirm(owner, bookingId).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("BOOKING_INVENTORY_INCONSISTENT"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE booking_id=?",
                Integer.class, bookingId)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id=?",
                String.class, bookingId)).isEqualTo("PENDING");
    }

    private long createBooking(UserAuth owner, String token) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(bookingBody(token)))
                .andExpect(status().isCreated()).andReturn();
        Number id = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.bookingId");
        return id.longValue();
    }

    private ResultActions confirm(UserAuth user, long bookingId) throws Exception {
        return mvc.perform(post("/api/v1/bookings/{id}/payments/mock-confirm", bookingId)
                .header("Authorization", "Bearer " + user.token()));
    }

    private ResultActions ticket(UserAuth user, long bookingId) throws Exception {
        return mvc.perform(get("/api/v1/bookings/{id}/ticket", bookingId)
                .header("Authorization", "Bearer " + user.token()));
    }
}
