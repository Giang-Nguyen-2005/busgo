package com.busgo;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.busgo.booking.entity.BookingStatus;
import com.busgo.common.exception.BusinessException;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class M8BookingIT extends M8BookingTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void bookingEndpointsRequireCustomerAuthentication() throws Exception {
        mvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/bookings/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/bookings/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void multiSeatHoldCreatesPendingServerPricedBookingAndConvertsOnlyHeldSegments()
            throws Exception {
        Fixture f = fixture();
        UserAuth owner = customer("m8-create");
        String holdToken = hold(owner, f, 0, 2, 0, 1).holdToken();

        MvcResult created = mvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON).content(bookingBody(holdToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("data.status").value("PENDING"))
                .andExpect(jsonPath("data.bookingCode").value(org.hamcrest.Matchers.matchesPattern("BG-[A-F0-9]{16}")))
                .andExpect(jsonPath("data.tripId").value(f.trip().getId()))
                .andExpect(jsonPath("data.pickup.tripStopId").value(f.stops().get(0).getId()))
                .andExpect(jsonPath("data.dropoff.tripStopId").value(f.stops().get(2).getId()))
                .andExpect(jsonPath("data.contact.name").value("Nguyen Giang"))
                .andExpect(jsonPath("data.contact.email").value("giang@example.test"))
                .andExpect(jsonPath("data.seats.length()").value(2))
                .andExpect(jsonPath("data.pricePerSeat").value(200.00))
                .andExpect(jsonPath("data.totalAmount").value(400.00))
                .andReturn();
        Number bookingId = com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.data.bookingId");

        assertThat(jdbc.queryForObject("SELECT customer_id FROM bookings WHERE id=?",
                Long.class, bookingId.longValue())).isEqualTo(owner.user().id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking_items WHERE booking_id=?",
                Integer.class, bookingId.longValue())).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM trip_seat_segment_inventory
                WHERE trip_seat_id IN (?, ?) AND trip_segment_id IN (?, ?)
                  AND status='BOOKED' AND booking_item_id IS NOT NULL
                  AND hold_token IS NULL AND held_by_user_id IS NULL AND hold_expires_at IS NULL
                """, Integer.class, f.seats().get(0).getId(), f.seats().get(1).getId(),
                f.segments().get(0).getId(), f.segments().get(1).getId())).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM trip_seat_segment_inventory
                WHERE trip_seat_id IN (?, ?) AND trip_segment_id=? AND status='AVAILABLE'
                """, Integer.class, f.seats().get(0).getId(), f.seats().get(1).getId(),
                f.segments().get(2).getId())).isEqualTo(2);

        assertThatThrownBy(() -> hold(owner, f, 0, 3, 0))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("SEAT_NOT_AVAILABLE"));
        assertThat(hold(owner, f, 2, 3, 0).status().name()).isEqualTo("ACTIVE");
    }

    @Test
    void invalidForeignExpiredCorruptAndMissingFareHoldsFailWithoutPartialBooking()
            throws Exception {
        Fixture f = fixture();
        UserAuth owner = customer("m8-errors-owner");
        UserAuth other = customer("m8-errors-other");

        requestBooking(other, "does-not-exist").andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("SEAT_HOLD_NOT_FOUND"));
        String foreign = hold(owner, f, 0, 1, 0).holdToken();
        requestBooking(other, foreign).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("SEAT_HOLD_NOT_FOUND"));

        jdbc.update("UPDATE trip_seat_segment_inventory SET hold_expires_at=? WHERE hold_token=?",
                LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1), foreign);
        requestBooking(owner, foreign).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("SEAT_HOLD_EXPIRED"));
        assertThat(bookingCount(f)).isZero();

        String corrupt = hold(owner, f, 0, 2, 1).holdToken();
        jdbc.update("""
                UPDATE trip_seat_segment_inventory SET status='AVAILABLE'
                WHERE hold_token=? ORDER BY id LIMIT 1
                """, corrupt);
        requestBooking(owner, corrupt).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("SEAT_NOT_AVAILABLE"));
        assertThat(bookingCount(f)).isZero();

        jdbc.update("""
                UPDATE trip_seat_segment_inventory
                SET status='AVAILABLE', hold_token=NULL, held_by_user_id=NULL, hold_expires_at=NULL
                WHERE hold_token=?
                """, corrupt);

        String incomplete = hold(owner, f, 0, 2, 0, 1).holdToken();
        jdbc.update("DELETE FROM trip_seat_segment_inventory WHERE hold_token=? ORDER BY id LIMIT 1",
                incomplete);
        requestBooking(owner, incomplete).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("SEAT_NOT_AVAILABLE"));
        assertThat(bookingCount(f)).isZero();
        jdbc.update("""
                UPDATE trip_seat_segment_inventory
                SET status='AVAILABLE', hold_token=NULL, held_by_user_id=NULL, hold_expires_at=NULL
                WHERE hold_token=?
                """, incomplete);

        String noFare = hold(owner, f, 1, 3, 1).holdToken();
        jdbc.update("""
                UPDATE operator_route_fares SET status='INACTIVE'
                WHERE operator_route_id=? AND from_route_stop_id=? AND to_route_stop_id=?
                """, f.operatorRoute().getId(), f.stops().get(1).getSourceRouteStop().getId(),
                f.stops().get(3).getSourceRouteStop().getId());
        requestBooking(owner, noFare).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("TRIP_NOT_BOOKABLE"));
        assertThat(bookingCount(f)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM booking_items item
                JOIN bookings booking ON booking.id=item.booking_id WHERE booking.trip_id=?
                """, Integer.class, f.trip().getId())).isZero();
    }

    @Test
    void ownerHistoryPaginationStatusFilterAndDetailHideForeignBooking() throws Exception {
        Fixture f = fixture();
        UserAuth owner = customer("m8-read-owner");
        UserAuth other = customer("m8-read-other");
        Number firstId = create(owner, hold(owner, f, 0, 1, 0).holdToken());
        Number secondId = create(owner, hold(owner, f, 0, 2, 1).holdToken());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(DISTINCT booking_code) FROM bookings WHERE trip_id=?
                """, Integer.class, f.trip().getId())).isEqualTo(2);

        mvc.perform(get("/api/v1/bookings/me").param("page", "0").param("size", "1")
                        .param("status", BookingStatus.PENDING.name())
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("data.length()").value(1))
                .andExpect(jsonPath("pagination.totalElements").value(2))
                .andExpect(jsonPath("pagination.totalPages").value(2));
        mvc.perform(get("/api/v1/bookings/{id}", secondId.longValue())
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("data.bookingId").value(secondId))
                .andExpect(jsonPath("data.seats[0].seatCode").value("A02"));
        mvc.perform(get("/api/v1/bookings/{id}", firstId.longValue())
                        .header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("BOOKING_NOT_FOUND"));
    }

    private ResultActions requestBooking(UserAuth user, String holdToken) throws Exception {
        return mvc.perform(post("/api/v1/bookings")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON).content(bookingBody(holdToken)));
    }

    private Number create(UserAuth owner, String token) throws Exception {
        MvcResult result = requestBooking(owner, token).andExpect(status().isCreated()).andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(),
                "$.data.bookingId");
    }

    private int bookingCount(Fixture fixture) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE trip_id=?",
                Integer.class, fixture.trip().getId());
    }
}
