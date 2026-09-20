package com.busgo;

import com.busgo.common.entity.ActiveStatus;
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
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

/** Run with mvn verify -Pmysql-integration against a dedicated MySQL database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Transactional
class CoreDatabaseIT extends JwtTestSupport {
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserRoleRepository userRoles;
    @Autowired TransportOperatorRepository operators;
    @Autowired OperatorStaffRepository staff;
    @Autowired LocationRepository locations;
    @Autowired RouteRepository routes;
    @Autowired RouteStopRepository stops;
    @Autowired OperatorRouteRepository operatorRoutes;
    @Autowired OperatorRouteFareRepository fares;
    @Autowired BusTypeRepository busTypes;
    @Autowired SeatTemplateRepository templates;
    @Autowired BusRepository buses;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired Environment environment;

    @Test
    void flywayOwnsSchemaAndSeedsExactlyFourRoles() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        flyway.validate();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().applied()).hasSize(7);
        assertThat(roles.findAll()).extracting(Role::getCode).containsExactlyInAnyOrder(RoleCode.values());
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = DATABASE()
                """, String.class)).containsExactlyInAnyOrder(
                "flyway_schema_history", "users", "roles", "user_roles", "transport_operators",
                "operator_staff", "locations", "routes", "route_stops", "operator_routes",
                "operator_route_fares", "bus_types", "seat_templates", "buses", "refresh_tokens",
                "trips", "trip_stops", "trip_segments", "trip_seats",
                "trip_seat_segment_inventory", "bookings", "booking_items");
    }

    @Test
    void allMappingsRoundTripWithRelationshipsAndExactDecimals() {
        var f = fixture();
        em.flush();
        em.clear();
        var loaded = fares.findById(f.fare().getId()).orElseThrow();
        assertThat(loaded.getPrice()).isEqualByComparingTo("650000.25");
        assertThat(loaded.getOperatorRoute().getOperator().getId()).isEqualTo(f.operator().getId());
        assertThat(loaded.getFromRouteStop().getRoute().getId()).isEqualTo(f.route().getId());
        assertThat(loaded.getToRouteStop().getStopOrder()).isEqualTo(2);
        assertThat(stops.findByRouteIdOrderByStopOrderAsc(f.route().getId()))
                .extracting(RouteStop::getStopOrder).containsExactly(1, 2);
        assertThat(userRoles.findById(new UserRoleId(f.user().getId(), f.role().getId()))).isPresent();
        assertThat(staff.findById(f.staff().getId()).orElseThrow().getOperator().getId()).isEqualTo(f.operator().getId());
        var seat = templates.findById(f.template().getId()).orElseThrow();
        assertThat(seat.getBusType().getId()).isEqualTo(f.busType().getId());
        assertThat(seat.getRow()).isEqualTo(1);
        assertThat(seat.getColumn()).isEqualTo(2);
        assertThat(seat.getFloor()).isEqualTo(1);
        assertThat(seat.getSeatCode()).isEqualTo("A01");
        assertThat(seat.getSeatType()).isEqualTo(SeatType.STANDARD);
        assertThat(seat.isActive()).isTrue();
        assertThat(buses.findById(f.bus().getId()).orElseThrow().getOperator().getId()).isEqualTo(f.operator().getId());
        assertThat(locations.findById(f.origin().getId()).orElseThrow().getLatitude()).isEqualByComparingTo("12.1234567");
        assertThat(routes.findById(f.route().getId()).orElseThrow().getEstimatedDistanceKm()).isEqualByComparingTo("1250.25");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void duplicateEmailRejectedByRepository() {
        var first = users.saveAndFlush(user());
        var duplicate = user();
        duplicate.setEmail(first.getEmail());
        assertThatThrownBy(() -> users.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateLicensePlateRejectedByRepository() {
        var f = fixture();
        var duplicate = new Bus();
        duplicate.setOperator(f.operator());
        duplicate.setBusType(f.busType());
        duplicate.setLicensePlate(f.bus().getLicensePlate());
        duplicate.setStatus(BusStatus.AVAILABLE);
        assertThatThrownBy(() -> buses.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateStopOrderRejectedByRepository() {
        var f = fixture();
        var newLocation = locations.saveAndFlush(location());
        var duplicate = stop(f.route(), newLocation, 1);
        assertThatThrownBy(() -> stops.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateLocationOnRouteRejectedByRepository() {
        var f = fixture();
        var duplicate = stop(f.route(), f.origin(), 3);
        assertThatThrownBy(() -> stops.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @CsvSource({
        "roles,code name",
        "user_roles,user_id role_id",
        "transport_operators,name code status created_at updated_at",
        "operator_staff,operator_id user_id staff_code status created_at",
        "operator_routes,operator_id route_id status created_at updated_at",
        "seat_templates,bus_type_id seat_code row_no column_no floor_no seat_type active"
    })
    void remainingUniqueKeysRejectDuplicates(String table, String columnList) {
        fixture();
        var columns = columnList.replace(' ', ',');
        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + table + " (" + columns + ") SELECT "
                + columns + " FROM " + table + " LIMIT 1")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @CsvSource({
        "user_roles,user_id,users", "user_roles,role_id,roles",
        "operator_staff,operator_id,transport_operators", "operator_staff,user_id,users",
        "routes,origin_location_id,locations", "routes,destination_location_id,locations",
        "route_stops,route_id,routes", "route_stops,location_id,locations",
        "operator_routes,operator_id,transport_operators", "operator_routes,route_id,routes",
        "operator_route_fares,operator_route_id,operator_routes",
        "operator_route_fares,from_route_stop_id,route_stops", "operator_route_fares,to_route_stop_id,route_stops",
        "seat_templates,bus_type_id,bus_types", "buses,operator_id,transport_operators", "buses,bus_type_id,bus_types"
    })
    void everyForeignKeyRejectsMissingParent(String table, String column, String parent) {
        fixture();
        Long missingId = jdbc.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM " + parent, Long.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE " + table + " SET " + column + " = ? LIMIT 1", missingId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @CsvSource({
        "operator_route_fares,price,-0.01", "route_stops,stop_order,0",
        "route_stops,estimated_offset_minutes,-1", "routes,estimated_distance_km,-1",
        "routes,estimated_duration_min,-1", "bus_types,seat_count,-1",
        "users,status,UNKNOWN", "roles,code,UNKNOWN", "buses,status,UNKNOWN",
        "transport_operators,status,UNKNOWN", "seat_templates,seat_type,UNKNOWN"
    })
    void checksRejectInvalidValues(String table, String column, String value) {
        fixture();
        assertThatThrownBy(() -> jdbc.update("UPDATE " + table + " SET " + column + " = ? LIMIT 1", value))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .rootCause().isInstanceOfSatisfying(java.sql.SQLException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(3819));
    }

    @Test
    void referencesPreventDestructiveParentDeletion() {
        var f = fixture();
        assertThatThrownBy(() -> jdbc.update("DELETE FROM transport_operators WHERE id = ?", f.operator().getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void softDeleteAndStatusRetainRelationships() {
        var f = fixture();
        var created = f.user().getCreatedAt();
        f.user().setStatus(UserStatus.INACTIVE);
        f.user().setDeletedAt(LocalDateTime.now(ZoneOffset.UTC));
        f.bus().setStatus(BusStatus.INACTIVE);
        f.bus().setDeletedAt(LocalDateTime.now(ZoneOffset.UTC));
        em.flush();
        em.clear();
        var user = users.findById(f.user().getId()).orElseThrow();
        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(user.getCreatedAt()).isEqualTo(created.withNano(created.getNano() / 1000 * 1000));
        assertThat(user.getUpdatedAt()).isAfterOrEqualTo(user.getCreatedAt());
        assertThat(buses.findById(f.bus().getId()).orElseThrow().getDeletedAt()).isNotNull();
        assertThat(staff.findById(f.staff().getId()).orElseThrow().getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    void multipleOperatorsAndReverseRoutesRemainIndependent() {
        var f = fixture();
        var other = operators.saveAndFlush(operator());
        var shared = operatorRoute(other, f.route());
        operatorRoutes.saveAndFlush(shared);
        var reverse = routes.saveAndFlush(route(f.destination(), f.origin()));
        stops.saveAndFlush(stop(reverse, f.destination(), 1));
        stops.saveAndFlush(stop(reverse, f.origin(), 2));
        assertThat(shared.getOperator().getId()).isNotEqualTo(f.operator().getId());
        assertThat(reverse.getId()).isNotEqualTo(f.route().getId());
    }

    @Test
    void requiredFareReferencesCannotBeNull() {
        fixture();
        assertThatThrownBy(() -> jdbc.update("UPDATE operator_route_fares SET from_route_stop_id = NULL"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void routeStopIndexesSupportOrderedAndLocationLookup() {
        assertThat(jdbc.queryForList("""
                SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) AS cols
                FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'route_stops'
                GROUP BY index_name
                """, String.class)).contains("route_id,stop_order", "route_id,location_id", "location_id");
    }

    private Fixture fixture() {
        var user = users.saveAndFlush(user());
        var role = roles.findByCode(RoleCode.CUSTOMER).orElseThrow();
        userRoles.saveAndFlush(new UserRole(user, role));
        var operator = operators.saveAndFlush(operator());
        var employee = new OperatorStaff();
        employee.setOperator(operator); employee.setUser(user);
        employee.setStaffCode("STAFF-" + UUID.randomUUID()); employee.setStatus(ActiveStatus.ACTIVE);
        staff.saveAndFlush(employee);
        var origin = locations.saveAndFlush(location());
        var destination = locations.saveAndFlush(location());
        var route = routes.saveAndFlush(route(origin, destination));
        var from = stops.saveAndFlush(stop(route, origin, 1));
        var to = stops.saveAndFlush(stop(route, destination, 2));
        var operatorRoute = operatorRoutes.saveAndFlush(operatorRoute(operator, route));
        var fare = new OperatorRouteFare();
        fare.setOperatorRoute(operatorRoute); fare.setFromRouteStop(from); fare.setToRouteStop(to);
        fare.setPrice(new BigDecimal("650000.25")); fare.setStatus(ActiveStatus.ACTIVE);
        fares.saveAndFlush(fare);
        var type = new BusType();
        type.setName("Test type"); type.setSeatCount(1); type.setStatus(ActiveStatus.ACTIVE);
        busTypes.saveAndFlush(type);
        var template = new SeatTemplate();
        template.setBusType(type); template.setSeatCode("A01"); template.setRow(1);
        template.setColumn(2); template.setFloor(1); template.setSeatType(SeatType.STANDARD); template.setActive(true);
        templates.saveAndFlush(template);
        var bus = new Bus();
        bus.setOperator(operator); bus.setBusType(type);
        bus.setLicensePlate(UUID.randomUUID().toString().substring(0, 20)); bus.setStatus(BusStatus.AVAILABLE);
        buses.saveAndFlush(bus);
        return new Fixture(user, role, operator, employee, origin, destination, route, fare, type, template, bus);
    }

    private User user() {
        var user = new User();
        user.setFullName("Test User"); user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPhone("0900000000"); user.setPasswordHash("test-hash-placeholder"); user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private TransportOperator operator() {
        var operator = new TransportOperator();
        operator.setCode(UUID.randomUUID().toString()); operator.setName("Test operator");
        operator.setStatus(OperatorStatus.ACTIVE);
        return operator;
    }

    private Location location() {
        var location = new Location();
        location.setName("Test location"); location.setLatitude(new BigDecimal("12.1234567"));
        location.setStatus(ActiveStatus.ACTIVE);
        return location;
    }

    private Route route(Location origin, Location destination) {
        var route = new Route();
        route.setName("Test direction"); route.setOriginLocation(origin); route.setDestinationLocation(destination);
        route.setEstimatedDistanceKm(new BigDecimal("1250.25")); route.setEstimatedDurationMinutes(930);
        route.setStatus(RouteStatus.ACTIVE);
        return route;
    }

    private RouteStop stop(Route route, Location location, int order) {
        var stop = new RouteStop();
        stop.setRoute(route); stop.setLocation(location); stop.setStopOrder(order);
        stop.setAllowPickup(true); stop.setAllowDropoff(true); stop.setEstimatedOffsetMinutes((order - 1) * 60);
        stop.setStatus(ActiveStatus.ACTIVE);
        return stop;
    }

    private OperatorRoute operatorRoute(TransportOperator operator, Route route) {
        var link = new OperatorRoute();
        link.setOperator(operator); link.setRoute(route); link.setStatus(ActiveStatus.ACTIVE);
        return link;
    }

    private record Fixture(User user, Role role, TransportOperator operator, OperatorStaff staff,
            Location origin, Location destination, Route route, OperatorRouteFare fare,
            BusType busType, SeatTemplate template, Bus bus) {}
}
