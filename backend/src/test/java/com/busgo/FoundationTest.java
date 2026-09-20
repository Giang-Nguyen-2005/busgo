package com.busgo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FoundationTest extends JwtTestSupport {
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.user.repository.UserRepository users;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.user.repository.RoleRepository roles;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.user.repository.UserRoleRepository userRoles;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.auth.repository.RefreshTokenRepository refreshTokens;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.operator.repository.OperatorStaffRepository operatorStaff;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.location.repository.LocationRepository locations;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.fleet.repository.BusTypeRepository busTypes;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.fleet.repository.SeatTemplateRepository seatTemplates;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.fleet.repository.BusRepository buses;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.route.repository.RouteRepository routes;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.route.repository.RouteStopRepository routeStops;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.route.repository.OperatorRouteRepository operatorRoutes;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.route.repository.OperatorRouteFareRepository routeFares;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.trip.repository.TripRepository trips;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.trip.repository.TripStopSnapshotRepository tripStops;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.trip.repository.TripSegmentRepository tripSegments;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.trip.repository.TripSeatRepository tripSeats;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.trip.repository.TripSeatSegmentInventoryRepository tripInventory;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.trip.search.TripSearchRepository tripSearch;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.trip.search.SeatAvailabilityQueryRepository seatAvailability;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.hold.SeatHoldInventoryRepository seatHoldInventory;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.booking.repository.BookingRepository bookings;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.booking.repository.BookingItemRepository bookingItems;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.booking.BookingInventoryRepository bookingInventory;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.booking.repository.BookingStatusHistoryRepository bookingHistories;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.payment.repository.PaymentRepository payments;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.ticket.repository.TicketRepository tickets;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.busgo.payment.BookingPaymentInventoryRepository paymentInventory;
    @org.springframework.test.context.bean.override.mockito.MockitoBean jakarta.persistence.EntityManager entityManager;
    @Autowired MockMvc mvc;
    @Autowired RoleProbe roleProbe;

    @org.springframework.boot.test.context.TestConfiguration
    static class RoleTestConfig {
        @org.springframework.context.annotation.Bean RoleProbe roleProbe() { return new RoleProbe(); }
    }
    static class RoleProbe {
        @org.springframework.security.access.prepost.PreAuthorize("hasRole('SYSTEM_ADMIN')")
        public String adminOnly() { return "allowed"; }
    }

    @Test @WithMockUser(roles="CUSTOMER")
    void roleAuthorizationDeniesUnprivilegedUsers() {
        org.assertj.core.api.Assertions.assertThatThrownBy(()->roleProbe.adminOnly())
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test @WithMockUser(roles="SYSTEM_ADMIN")
    void roleAuthorizationAllowsRequiredRole() {
        org.assertj.core.api.Assertions.assertThat(roleProbe.adminOnly()).isEqualTo("allowed");
    }

    @Test
    void publicHealthReturnsExactContract() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"data\":{\"status\":\"UP\"}}", true));
    }

    @Test
    void otherEndpointsRequireAuthenticationWithoutRedirect() throws Exception {
        mvc.perform(get("/api/v1/private"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("timestamp").isString())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void seatHoldEndpointsRequireAuthentication() throws Exception {
        mvc.perform(post("/api/v1/seat-holds"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/seat-holds/token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/seat-holds/token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void authenticatedRequestsAreDeniedUntilAccessRulesExist() throws Exception {
        mvc.perform(get("/api/v1/private"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("ACCESS_DENIED"));
    }
}
