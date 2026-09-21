package com.busgo;

import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.security.*;
import com.busgo.fleet.entity.*;
import com.busgo.fleet.repository.*;
import com.busgo.location.entity.Location;
import com.busgo.location.repository.LocationRepository;
import com.busgo.operator.entity.*;
import com.busgo.operator.repository.*;
import com.busgo.route.entity.*;
import com.busgo.route.repository.*;
import com.busgo.user.entity.*;
import com.busgo.user.repository.*;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class M3ManagementIT extends JwtTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserRoleRepository userRoles;
    @Autowired TransportOperatorRepository operators;
    @Autowired OperatorStaffRepository staff;
    @Autowired LocationRepository locations;
    @Autowired BusTypeRepository busTypes;
    @Autowired SeatTemplateRepository seats;
    @Autowired BusRepository buses;
    @Autowired RouteRepository routes;
    @Autowired RouteStopRepository routeStops;
    @Autowired OperatorRouteRepository operatorRoutes;

    @Test
    void publicLocationLookupReturnsOnlyActiveMatchingLocations() throws Exception {
        String marker = "lookup-" + UUID.randomUUID();
        location(marker + " active", "Da Nang", "Hai Chau", ActiveStatus.ACTIVE);
        location(marker + " inactive", "Lam Dong", "Da Lat", ActiveStatus.INACTIVE);
        location("Ha Noi Station", "Ha Noi", "Hoang Mai", ActiveStatus.ACTIVE);

        mvc.perform(get("/api/v1/locations").param("q", marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.length()").value(1))
                .andExpect(jsonPath("data[0].name").value(marker + " active"));
    }

    @Test
    void customerCannotAccessOperatorManagement() throws Exception {
        String token = token(account(RoleCode.CUSTOMER, null));
        mvc.perform(get("/api/v1/operator/buses").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("ACCESS_DENIED"));
    }

    @Test
    void adminCreatesUpdatesAndReadsOnlyOwnBusesAndCannotSupplyOwnership() throws Exception {
        Admin a = admin();
        Admin b = admin();
        BusType type = validBusType(2);

        JsonNode created = data(postJson("/api/v1/operator/buses", a.token(), Map.of(
                "licensePlate", plate(), "busTypeId", type.getId(), "operatorId", b.operator().getId()))
                .andExpect(status().isCreated()));
        long busId = created.path("id").asLong();
        org.assertj.core.api.Assertions.assertThat(buses.findById(busId).orElseThrow().getOperator().getId())
                .isEqualTo(a.operator().getId());

        mvc.perform(get("/api/v1/operator/buses/{id}", busId).header("Authorization", bearer(b.token())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("BUS_NOT_FOUND"));
        patchJson("/api/v1/operator/buses/" + busId, b.token(), Map.of("status", "INACTIVE", "operatorId", b.operator().getId()))
                .andExpect(status().isNotFound());
        patchJson("/api/v1/operator/buses/" + busId, a.token(), Map.of("status", "MAINTENANCE"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.status").value("MAINTENANCE"));
    }

    @Test
    void duplicateLicensePlateAndInvalidSeatLayoutAreRejected() throws Exception {
        Admin admin = admin();
        BusType valid = validBusType(1);
        String plate = plate();
        postJson("/api/v1/operator/buses", admin.token(), Map.of("licensePlate", plate, "busTypeId", valid.getId()))
                .andExpect(status().isCreated());
        postJson("/api/v1/operator/buses", admin.token(), Map.of("licensePlate", plate.toLowerCase(Locale.ROOT), "busTypeId", valid.getId()))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("LICENSE_PLATE_ALREADY_EXISTS"));

        BusType invalid = busType(2);
        seat(invalid, "A01", 1, 1, 1);
        seat(invalid, "A02", 1, 1, 1);
        postJson("/api/v1/operator/buses", admin.token(), Map.of("licensePlate", plate(), "busTypeId", invalid.getId()))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("INVALID_BUS_TYPE"));
    }

    @Test
    void duplicateSeatCodeIsEnforcedByMysql() {
        BusType type = busType(2);
        seat(type, "A01", 1, 1, 1);
        assertThatThrownBy(() -> seat(type, "A01", 1, 2, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void routeAttachmentValidatesLayoutAndUniqueAssociation() throws Exception {
        Admin admin = admin();
        RouteFixture valid = routeFixture(0, 60);
        JsonNode attached = data(postJson("/api/v1/operator/routes", admin.token(), Map.of(
                "routeId", valid.route().getId(), "operatorId", 999999))
                .andExpect(status().isCreated()));
        long associationId = attached.path("id").asLong();
        postJson("/api/v1/operator/routes", admin.token(), Map.of("routeId", valid.route().getId()))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("OPERATOR_ROUTE_ALREADY_EXISTS"));
        patchJson("/api/v1/operator/routes/" + associationId, admin.token(), Map.of("status", "INACTIVE", "routeId", -1))
                .andExpect(status().isOk()).andExpect(jsonPath("data.status").value("INACTIVE"));

        RouteFixture invalid = routeFixture(0, 0);
        postJson("/api/v1/operator/routes", admin.token(), Map.of("routeId", invalid.route().getId()))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("INVALID_ROUTE"));
    }

    @Test
    void foreignOperatorRouteIsNotFoundForReadUpdateAndFareAccess() throws Exception {
        Admin a = admin();
        Admin b = admin();
        OperatorRoute association = association(a.operator(), routeFixture(0, 60).route());
        mvc.perform(get("/api/v1/operator/routes/{id}", association.getId()).header("Authorization", bearer(b.token())))
                .andExpect(status().isNotFound());
        patchJson("/api/v1/operator/routes/" + association.getId(), b.token(), Map.of("status", "INACTIVE"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/operator/routes/{id}/fares", association.getId()).header("Authorization", bearer(b.token())))
                .andExpect(status().isNotFound());
    }

    @Test
    void fareRejectsCrossRouteReverseAndNonPositivePriceAndAcceptsValidPair() throws Exception {
        Admin admin = admin();
        RouteFixture first = routeFixture(0, 60);
        RouteFixture second = routeFixture(0, 90);
        OperatorRoute association = association(admin.operator(), first.route());

        putJson(farePath(association), admin.token(), fareBody(first.from().getId(), second.to().getId(), 100))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_ROUTE_FARE"));
        putJson(farePath(association), admin.token(), fareBody(first.to().getId(), first.from().getId(), 100))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_ROUTE_FARE"));
        putJson(farePath(association), admin.token(), fareBody(first.from().getId(), first.to().getId(), 0))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        putJson(farePath(association), admin.token(), fareBody(first.from().getId(), first.to().getId(), 250000))
                .andExpect(status().isOk()).andExpect(jsonPath("data[0].price").value(250000))
                .andExpect(jsonPath("data[0].status").value("ACTIVE"));
    }

    private ResultActions postJson(String path, String token, Object body) throws Exception {
        return mvc.perform(post(path).header("Authorization", bearer(token)).contentType("application/json")
                .content(json.writeValueAsBytes(body)));
    }
    private ResultActions patchJson(String path, String token, Object body) throws Exception {
        return mvc.perform(patch(path).header("Authorization", bearer(token)).contentType("application/json")
                .content(json.writeValueAsBytes(body)));
    }
    private ResultActions putJson(String path, String token, Object body) throws Exception {
        return mvc.perform(put(path).header("Authorization", bearer(token)).contentType("application/json")
                .content(json.writeValueAsBytes(body)));
    }
    private JsonNode data(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray()).path("data");
    }
    private static String bearer(String token) { return "Bearer " + token; }
    private static String farePath(OperatorRoute route) { return "/api/v1/operator/routes/" + route.getId() + "/fares"; }
    private static Map<String, Object> fareBody(Long from, Long to, int price) {
        return Map.of("fares", List.of(Map.of("fromRouteStopId", from, "toRouteStopId", to, "price", price)));
    }

    private Admin admin() {
        TransportOperator operator = new TransportOperator();
        operator.setName("Operator " + UUID.randomUUID());
        operator.setCode(UUID.randomUUID().toString());
        operator.setStatus(OperatorStatus.ACTIVE);
        operators.saveAndFlush(operator);
        User user = account(RoleCode.OPERATOR_ADMIN, operator);
        return new Admin(operator, token(user));
    }

    private User account(RoleCode code, TransportOperator operator) {
        User user = new User();
        user.setFullName("M3 user");
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPhone("0901234567");
        user.setPasswordHash("not-used-by-this-test");
        user.setStatus(UserStatus.ACTIVE);
        users.saveAndFlush(user);
        Role role = roles.findByCode(code).orElseThrow();
        userRoles.saveAndFlush(new UserRole(user, role));
        if (operator != null) {
            OperatorStaff membership = new OperatorStaff();
            membership.setOperator(operator);
            membership.setUser(user);
            membership.setStaffCode(UUID.randomUUID().toString());
            membership.setStatus(ActiveStatus.ACTIVE);
            staff.saveAndFlush(membership);
        }
        return user;
    }

    private String token(User user) {
        return jwt.issue(new CurrentUser(user.getId(), userRoles.findRoleCodesByUserId(user.getId())), "access");
    }

    private Location location(String name, String province, String district, ActiveStatus status) {
        Location location = new Location();
        location.setName(name); location.setProvince(province); location.setDistrict(district); location.setStatus(status);
        return locations.saveAndFlush(location);
    }

    private BusType validBusType(int count) {
        BusType type = busType(count);
        for (int i = 1; i <= count; i++) seat(type, "A" + i, 1, i, 1);
        return type;
    }
    private BusType busType(int count) {
        BusType type = new BusType();
        type.setName("Type " + UUID.randomUUID()); type.setSeatCount(count); type.setStatus(ActiveStatus.ACTIVE);
        return busTypes.saveAndFlush(type);
    }
    private SeatTemplate seat(BusType type, String code, int row, int column, int floor) {
        SeatTemplate seat = new SeatTemplate();
        seat.setBusType(type); seat.setSeatCode(code); seat.setRow(row); seat.setColumn(column); seat.setFloor(floor);
        seat.setSeatType(SeatType.STANDARD); seat.setActive(true);
        return seats.saveAndFlush(seat);
    }

    private RouteFixture routeFixture(int firstOffset, int secondOffset) {
        Location from = location("Origin " + UUID.randomUUID(), "Province", "District", ActiveStatus.ACTIVE);
        Location to = location("Destination " + UUID.randomUUID(), "Province", "District", ActiveStatus.ACTIVE);
        Route route = new Route();
        route.setName("Route " + UUID.randomUUID()); route.setOriginLocation(from); route.setDestinationLocation(to);
        route.setEstimatedDistanceKm(new BigDecimal("100.00")); route.setEstimatedDurationMinutes(60); route.setStatus(RouteStatus.ACTIVE);
        routes.saveAndFlush(route);
        RouteStop first = stop(route, from, 1, firstOffset);
        RouteStop second = stop(route, to, 2, secondOffset);
        return new RouteFixture(route, first, second);
    }
    private RouteStop stop(Route route, Location location, int order, int offset) {
        RouteStop stop = new RouteStop();
        stop.setRoute(route); stop.setLocation(location); stop.setStopOrder(order); stop.setEstimatedOffsetMinutes(offset);
        stop.setAllowPickup(order == 1); stop.setAllowDropoff(order != 1); stop.setStatus(ActiveStatus.ACTIVE);
        return routeStops.saveAndFlush(stop);
    }
    private OperatorRoute association(TransportOperator operator, Route route) {
        OperatorRoute association = new OperatorRoute();
        association.setOperator(operator); association.setRoute(route); association.setStatus(ActiveStatus.ACTIVE);
        return operatorRoutes.saveAndFlush(association);
    }
    private static String plate() { return "M3-" + UUID.randomUUID().toString().substring(0, 8); }
    private record Admin(TransportOperator operator, String token) {}
    private record RouteFixture(Route route, RouteStop from, RouteStop to) {}
}
