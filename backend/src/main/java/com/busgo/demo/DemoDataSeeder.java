package com.busgo.demo;

import static com.busgo.common.time.BusGoTime.BUSINESS_ZONE;
import static com.busgo.common.time.BusGoTime.businessTime;

import com.busgo.common.entity.ActiveStatus;
import com.busgo.fleet.entity.Bus;
import com.busgo.fleet.entity.BusStatus;
import com.busgo.fleet.entity.BusType;
import com.busgo.fleet.entity.SeatTemplate;
import com.busgo.fleet.entity.SeatType;
import com.busgo.fleet.repository.BusRepository;
import com.busgo.fleet.repository.BusTypeRepository;
import com.busgo.fleet.repository.SeatTemplateRepository;
import com.busgo.location.entity.Location;
import com.busgo.location.repository.LocationRepository;
import com.busgo.operator.entity.OperatorStatus;
import com.busgo.operator.entity.OperatorStaff;
import com.busgo.operator.entity.TransportOperator;
import com.busgo.operator.repository.OperatorStaffRepository;
import com.busgo.operator.repository.TransportOperatorRepository;
import com.busgo.route.entity.OperatorRoute;
import com.busgo.route.entity.OperatorRouteFare;
import com.busgo.route.entity.Route;
import com.busgo.route.entity.RouteStatus;
import com.busgo.route.entity.RouteStop;
import com.busgo.route.repository.OperatorRouteFareRepository;
import com.busgo.route.repository.OperatorRouteRepository;
import com.busgo.route.repository.RouteRepository;
import com.busgo.route.repository.RouteStopRepository;
import com.busgo.trip.TripAggregateCreator;
import com.busgo.trip.entity.Trip;
import com.busgo.trip.repository.TripRepository;
import com.busgo.user.entity.Role;
import com.busgo.user.entity.RoleCode;
import com.busgo.user.entity.User;
import com.busgo.user.entity.UserRole;
import com.busgo.user.entity.UserStatus;
import com.busgo.user.repository.RoleRepository;
import com.busgo.user.repository.UserRepository;
import com.busgo.user.repository.UserRoleRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/** Explicit-profile, repeatable portfolio data. Never active in production by default. */
@Component
@Profile("demo & !prod & !production")
public class DemoDataSeeder implements ApplicationRunner {
    public static final String OPERATOR_ADMIN_EMAIL = "operator.admin@anphu-demo.example";
    public static final String OPERATOR_ADMIN_PASSWORD = "DemoOperator!2026";
    public static final String OPERATOR_STAFF_EMAIL = "operator.staff@anphu-demo.example";
    public static final String OPERATOR_STAFF_PASSWORD = "DemoStaff!2026";
    public static final String SECONDARY_ADMIN_EMAIL = "operator.admin@minhthanh-demo.example";
    public static final String SECONDARY_ADMIN_PASSWORD = "DemoSecondary!2026";
    private static final String OPERATOR_ADMIN_NAME = "Demo An Phu Operator Admin";
    private static final String OPERATOR_ADMIN_PHONE = "0900001101";
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final TransportOperatorRepository operators;
    private final LocationRepository locations;
    private final RouteRepository routes;
    private final RouteStopRepository routeStops;
    private final OperatorRouteRepository operatorRoutes;
    private final OperatorRouteFareRepository fares;
    private final BusTypeRepository busTypes;
    private final SeatTemplateRepository seatTemplates;
    private final BusRepository buses;
    private final TripRepository trips;
    private final TripAggregateCreator aggregateCreator;
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final OperatorStaffRepository operatorStaff;
    private final PasswordEncoder passwords;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean reset;

    public DemoDataSeeder(TransportOperatorRepository operators, LocationRepository locations,
            RouteRepository routes, RouteStopRepository routeStops,
            OperatorRouteRepository operatorRoutes, OperatorRouteFareRepository fares,
            BusTypeRepository busTypes, SeatTemplateRepository seatTemplates,
            BusRepository buses, TripRepository trips, TripAggregateCreator aggregateCreator,
            UserRepository users, RoleRepository roles, UserRoleRepository userRoles,
            OperatorStaffRepository operatorStaff, PasswordEncoder passwords,
            JdbcTemplate jdbc, Clock clock,
            @Value("${busgo.demo.reset-unbooked-trips:false}") boolean reset) {
        this.operators = operators;
        this.locations = locations;
        this.routes = routes;
        this.routeStops = routeStops;
        this.operatorRoutes = operatorRoutes;
        this.fares = fares;
        this.busTypes = busTypes;
        this.seatTemplates = seatTemplates;
        this.buses = buses;
        this.trips = trips;
        this.aggregateCreator = aggregateCreator;
        this.users = users;
        this.roles = roles;
        this.userRoles = userRoles;
        this.operatorStaff = operatorStaff;
        this.passwords = passwords;
        this.jdbc = jdbc;
        this.clock = clock;
        this.reset = reset;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void run(ApplicationArguments args) {
        if (reset) log.warn("Demo reset is disabled: existing trips and inventory are never deleted.");
        SeedResult result = seed();
        log.info("BusGo demo seed finished: search {} -> {} on {} ({} created, {} reused, {} skipped). Verify availability before the demo.",
                result.pickup().getName(), result.dropoff().getName(), result.searchDate(),
                result.createdTrips(), result.reusedTrips(), result.skippedTrips());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SeedResult seed() {
        Map<String, TransportOperator> operator = seedOperators();
        seedOperatorAdmin(operator.get("DEMO-ANPHU"));
        seedAccount(operator.get("DEMO-ANPHU"), OPERATOR_STAFF_EMAIL, OPERATOR_STAFF_PASSWORD,
                "Demo An Phu Operator Staff", "0900001102", RoleCode.OPERATOR_STAFF, "DEMO-ANPHU-STAFF");
        // A secondary operator must retain a login-capable admin for safe reactivation.
        seedAccount(operator.get("DEMO-MINHTHANH"), SECONDARY_ADMIN_EMAIL, SECONDARY_ADMIN_PASSWORD,
                "Demo Minh Thanh Operator Admin", "0900001104", RoleCode.OPERATOR_ADMIN, "DEMO-MINHTHANH-ADMIN");
        Map<String, Location> location = seedLocations();
        Map<String, RouteBundle> route = seedRoutes(location);
        Map<String, BusType> type = seedBusTypes();
        Map<String, Bus> bus = seedBuses(operator, type);
        Map<String, OperatorRoute> association = seedAssociationsAndFares(operator, route);
        seedOperationalEmployees(operator.get("DEMO-ANPHU").getId());

        LocalDate searchDate = LocalDate.now(clock.withZone(BUSINESS_ZONE)).plusDays(1);
        int created = 0;
        int reused = 0;
        int skipped = 0;
        for (int day = 0; day < 3; day++) for (TripSpec spec : tripSpecs()) {
            OperatorRoute operatorRoute = association.get(key(spec.operatorCode(), spec.routeKey()));
            Bus assignedBus = bus.get(spec.plate());
            LocalDateTime departure = businessTime(searchDate.plusDays(day), spec.time());
            Trip trip = trips.findByOperatorRouteIdAndBusIdAndDepartureTime(
                    operatorRoute.getId(), assignedBus.getId(), departure).orElse(null);
            if (trip == null) {
                // Lock the same bus used by the production creator before checking its schedule.
                buses.findOwnedByIdForUpdate(assignedBus.getId(), operatorRoute.getOperator().getId())
                        .orElseThrow(() -> collision("bus " + spec.plate()));
                RouteBundle source = route.get(spec.routeKey());
                LocalDateTime arrival = departure.plusMinutes(source.stops().get(source.stops().size() - 1)
                        .getEstimatedOffsetMinutes());
                if (operatorRoute.getOperator().getStatus() != OperatorStatus.ACTIVE
                        || operatorRoute.getStatus() != ActiveStatus.ACTIVE
                        || source.route().getStatus() != RouteStatus.ACTIVE
                        || source.stops().stream().anyMatch(stop -> stop.getStatus() != ActiveStatus.ACTIVE)
                        || assignedBus.getStatus() != BusStatus.AVAILABLE
                        || assignedBus.getDeletedAt() != null
                        || assignedBus.getBusType().getStatus() != ActiveStatus.ACTIVE
                        || seatTemplates.findByBusTypeIdAndActiveTrueOrderByFloorAscRowAscColumnAsc(
                                assignedBus.getBusType().getId()).size() != assignedBus.getBusType().getSeatCount()
                        || !hasUsableFares(operatorRoute, source)
                        || trips.hasScheduleConflict(assignedBus.getId(), departure, arrival)) {
                    skipped++;
                    log.warn("Skipped demo trip: bus={}, route={}, departure={} UTC; existing schedule or inactive/unusable catalogue. Existing data preserved.",
                            spec.plate(), spec.routeKey(), departure);
                    continue;
                }
                trip = aggregateCreator.create(operatorRoute.getOperator().getId(), operatorRoute.getId(),
                        assignedBus.getId(), departure).trip();
                applyAvailability(trip.getId(), spec.blockedSeats(), spec.routeKey().equals("COASTAL"));
                if (spec.operatorCode().equals("DEMO-ANPHU")) seedNewTripCrew(trip);
                created++;
            } else {
                reused++;
            }
        }
        return new SeedResult(searchDate, location.get("HCM"), location.get("DALAT"), created, reused, skipped);
    }

    private void seedOperationalEmployees(long operatorId) {
        for (String duty : List.of("DRIVER", "ATTENDANT")) {
            String code = "DEMO-" + duty;
            if (jdbc.queryForObject("SELECT COUNT(*) FROM operator_employees WHERE operator_id=? AND employee_code=?", Long.class, operatorId, code) > 0) continue;
            jdbc.update("INSERT INTO operator_employees(operator_id,employee_code,full_name,phone,status,created_at,updated_at) VALUES(?,?,?,?,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", operatorId, code, duty.equals("DRIVER") ? "Tài xế An Phú Demo" : "Phụ xe An Phú Demo", duty.equals("DRIVER") ? "0900001201" : "0900001202");
            Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            jdbc.update("INSERT INTO employee_capabilities VALUES(?,?)", id, duty);
            if (duty.equals("DRIVER")) jdbc.update("INSERT INTO driver_profiles VALUES(?,'DEMO-LICENCE','DEMO','2035-12-31')", id);
        }
    }

    /** Only newly created trips receive seed crew. Never restore released or edited assignments. */
    private void seedNewTripCrew(Trip trip) {
        long operatorId = trip.getOperatorRoute().getOperator().getId();
        Long actorId = users.findByEmail(OPERATOR_ADMIN_EMAIL).orElseThrow().getId();
        var employees = jdbc.queryForList("SELECT id,employee_code,status FROM operator_employees WHERE operator_id=? AND employee_code IN ('DEMO-DRIVER','DEMO-ATTENDANT') ORDER BY id FOR UPDATE", operatorId);
        for (var employee : employees) {
            Long id = ((Number) employee.get("id")).longValue();
            String duty = employee.get("employee_code").equals("DEMO-DRIVER") ? "DRIVER" : "ATTENDANT";
            if (!employee.get("status").equals("ACTIVE") || jdbc.queryForObject("SELECT COUNT(*) FROM employee_capabilities WHERE employee_id=? AND capability=?", Long.class,id,duty)==0) continue;
            if (duty.equals("DRIVER") && jdbc.queryForObject("SELECT COUNT(*) FROM driver_profiles WHERE employee_id=? AND licence_expiry_date>=DATE(?)",Long.class,id,com.busgo.common.time.JpaJdbcTime.parameter(trip.getEstimatedArrivalTime().plusHours(7)))==0) continue;
            if (jdbc.queryForObject("SELECT COUNT(*) FROM trip_crew_assignments a JOIN trips t ON t.id=a.trip_id WHERE a.employee_id=? AND a.released_at IS NULL AND t.status IN ('SCHEDULED','BOARDING','DEPARTED') AND t.departure_time<? AND t.estimated_arrival_time>?",Long.class,id,com.busgo.common.time.JpaJdbcTime.parameter(trip.getEstimatedArrivalTime()),com.busgo.common.time.JpaJdbcTime.parameter(trip.getDepartureTime()))>0) continue;
            jdbc.update("INSERT INTO trip_crew_assignments(trip_id,employee_id,duty,assigned_at,assigned_by) VALUES(?,?,?,UTC_TIMESTAMP(6),?)",trip.getId(),id,duty,actorId);
            Long assignmentId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
            jdbc.update("INSERT INTO operational_history(trip_id,entity_type,entity_id,action,actor_id,occurred_at,reason) VALUES(?,'CREW',?,'CREW_ASSIGNED',?,UTC_TIMESTAMP(6),'Demo seed: newly created trip')",trip.getId(),assignmentId,actorId);
        }
    }

    /**
     * The reserved .example address identifies this fixture only.  It is never
     * looked up or changed outside the explicit demo profile.
     */
    private void seedOperatorAdmin(TransportOperator demoOperator) {
        seedAccount(demoOperator, OPERATOR_ADMIN_EMAIL, OPERATOR_ADMIN_PASSWORD,
                OPERATOR_ADMIN_NAME, OPERATOR_ADMIN_PHONE, RoleCode.OPERATOR_ADMIN, "DEMO-ANPHU-ADMIN");
    }

    private void seedAccount(TransportOperator demoOperator, String email, String password,
            String name, String phone, RoleCode roleCode, String staffCode) {
        User user = users.findByEmail(email).orElse(null);
        if (user != null) {
            List<OperatorStaff> memberships = operatorStaff.findByUserId(user.getId());
            if (!name.equals(user.getFullName()) || !phone.equals(user.getPhone()) || user.getDeletedAt() != null
                    || !userRoles.findRoleCodesByUserId(user.getId()).equals(List.of(roleCode))
                    || memberships.size() != 1
                    || !memberships.get(0).getOperator().getId().equals(demoOperator.getId())
                    || !staffCode.equals(memberships.get(0).getStaffCode())) throw collision("account " + email);
            // Account/membership status and password are intentionally preserved, including suspension.
            return;
        }
        if (operatorStaff.existsByOperatorIdAndStaffCodeIgnoreCase(demoOperator.getId(), staffCode))
            throw collision("staff code " + staffCode);
        user = new User(); user.setEmail(email); user.setFullName(name); user.setPhone(phone);
        user.setPasswordHash(passwords.encode(password)); user.setStatus(UserStatus.ACTIVE);
        users.saveAndFlush(user);
        Role role = roles.findByCode(roleCode).orElseThrow(() -> collision("missing role " + roleCode));
        userRoles.saveAndFlush(new UserRole(user, role));
        OperatorStaff membership = new OperatorStaff();
        membership.setOperator(demoOperator);
        membership.setUser(user);
        membership.setStaffCode(staffCode);
        membership.setStatus(ActiveStatus.ACTIVE);
        operatorStaff.saveAndFlush(membership);
    }

    private Map<String, TransportOperator> seedOperators() {
        Map<String, TransportOperator> result = new LinkedHashMap<>();
        for (OperatorSpec spec : List.of(
                new OperatorSpec("DEMO-ANPHU", "An Phú Express", "02873018686",
                        "hello@anphu-demo.example", "12 Đường Số 5, TP. Hồ Chí Minh"),
                new OperatorSpec("DEMO-MINHTHANH", "Minh Thành Limousine", "02633558899",
                        "hello@minhthanh-demo.example", "28 Đường Hoa Phượng, Đà Lạt"),
                new OperatorSpec("DEMO-TAYNGUYEN", "Tây Nguyên Travel", "02623887766",
                        "hello@taynguyen-demo.example", "45 Đường Cao Nguyên, Buôn Ma Thuột"))) {
            TransportOperator value = operators.findByCode(spec.code()).orElseGet(TransportOperator::new);
            if (value.getId() != null) {
                if (!spec.name().equals(value.getName()) || !spec.email().equals(value.getEmail())
                        || !spec.phone().equals(value.getPhone()) || !spec.address().equals(value.getAddress()))
                    throw collision("operator " + spec.code());
                result.put(spec.code(), operators.lockById(value.getId()).orElseThrow());
                continue;
            }
            value.setCode(spec.code());
            value.setName(spec.name());
            value.setPhone(spec.phone());
            value.setEmail(spec.email());
            value.setAddress(spec.address());
            value.setStatus(OperatorStatus.ACTIVE);
            result.put(spec.code(), operators.save(value));
        }
        operators.flush();
        return result;
    }

    private Map<String, Location> seedLocations() {
        Map<String, Location> result = new LinkedHashMap<>();
        for (LocationSpec spec : List.of(
                new LocationSpec("HCM", "Bến xe TP. Hồ Chí Minh", "TP. Hồ Chí Minh", "Thành phố Thủ Đức",
                        "Khu vực cửa ngõ phía Đông TP. Hồ Chí Minh"),
                new LocationSpec("DALAT", "Bến xe Đà Lạt", "Lâm Đồng", "Đà Lạt", "Khu vực trung tâm Đà Lạt"),
                new LocationSpec("BMT", "Bến xe Buôn Ma Thuột", "Đắk Lắk", "Buôn Ma Thuột", "Khu vực trung tâm Buôn Ma Thuột"),
                new LocationSpec("NHATRANG", "Bến xe Nha Trang", "Khánh Hòa", "Nha Trang", "Khu vực trung tâm Nha Trang"),
                new LocationSpec("DANANG", "Bến xe Đà Nẵng", "Đà Nẵng", "Cẩm Lệ", "Khu vực cửa ngõ Đà Nẵng"),
                new LocationSpec("HUE", "Bến xe Huế", "Thừa Thiên Huế", "Huế", "Khu vực trung tâm Huế"))) {
            Location value = locations.findFirstByNameAndProvince(spec.name(), spec.province()).orElseGet(Location::new);
            requireAtMostOne("location " + spec.key(), "SELECT COUNT(*) FROM locations WHERE name=? AND province=?",
                    spec.name(), spec.province());
            if (value.getId() != null) {
                if (!spec.district().equals(value.getDistrict()) || !spec.address().equals(value.getAddress()))
                    throw collision("location " + spec.key());
                result.put(spec.key(), value);
                continue;
            }
            value.setName(spec.name());
            value.setProvince(spec.province());
            value.setDistrict(spec.district());
            value.setAddress(spec.address());
            value.setStatus(ActiveStatus.ACTIVE);
            result.put(spec.key(), locations.save(value));
        }
        locations.flush();
        return result;
    }

    private Map<String, RouteBundle> seedRoutes(Map<String, Location> location) {
        Map<String, RouteBundle> result = new LinkedHashMap<>();
        List<RouteSpec> specs = List.of(
                direct("HCM-DALAT", "TP. Hồ Chí Minh → Đà Lạt", "HCM", "DALAT", 310, 420),
                direct("DALAT-HCM", "Đà Lạt → TP. Hồ Chí Minh", "DALAT", "HCM", 310, 420),
                direct("HCM-BMT", "TP. Hồ Chí Minh → Buôn Ma Thuột", "HCM", "BMT", 350, 480),
                direct("BMT-HCM", "Buôn Ma Thuột → TP. Hồ Chí Minh", "BMT", "HCM", 350, 480),
                direct("HCM-NHATRANG", "TP. Hồ Chí Minh → Nha Trang", "HCM", "NHATRANG", 430, 540),
                direct("NHATRANG-HCM", "Nha Trang → TP. Hồ Chí Minh", "NHATRANG", "HCM", 430, 540),
                direct("DANANG-HUE", "Đà Nẵng → Huế", "DANANG", "HUE", 100, 180),
                direct("HUE-DANANG", "Huế → Đà Nẵng", "HUE", "DANANG", 100, 180),
                new RouteSpec("COASTAL", "TP. Hồ Chí Minh → Nha Trang → Đà Nẵng → Huế",
                        "HCM", "HUE", 1040, 1260,
                        List.of(new StopSpec("HCM", 0), new StopSpec("NHATRANG", 540),
                                new StopSpec("DANANG", 1080), new StopSpec("HUE", 1260))));
        for (RouteSpec spec : specs) {
            Location origin = location.get(spec.origin());
            Location destination = location.get(spec.destination());
            Route value = routes.findFirstByNameAndOriginLocationIdAndDestinationLocationId(
                    spec.name(), origin.getId(), destination.getId()).orElseGet(Route::new);
            requireAtMostOne("route " + spec.key(),
                    "SELECT COUNT(*) FROM routes WHERE name=? AND origin_location_id=? AND destination_location_id=?",
                    spec.name(), origin.getId(), destination.getId());
            if (value.getId() != null) {
                List<RouteStop> existingStops = routeStops.findByRouteIdOrderByStopOrderAsc(value.getId());
                if (value.getEstimatedDistanceKm().compareTo(BigDecimal.valueOf(spec.distanceKm())) != 0
                        || value.getEstimatedDurationMinutes() != spec.durationMinutes()
                        || existingStops.size() != spec.stops().size()) throw collision("route " + spec.key());
                for (int index = 0; index < existingStops.size(); index++) {
                    RouteStop stop = existingStops.get(index);
                    StopSpec expected = spec.stops().get(index);
                    if (stop.getStopOrder() != index + 1
                            || !stop.getLocation().getId().equals(location.get(expected.location()).getId())
                            || stop.getEstimatedOffsetMinutes() != expected.offsetMinutes()
                            || stop.isAllowPickup() != (index < existingStops.size() - 1)
                            || stop.isAllowDropoff() != (index > 0)) throw collision("route stops " + spec.key());
                }
                result.put(spec.key(), new RouteBundle(value, existingStops));
                continue;
            }
            value.setName(spec.name());
            value.setOriginLocation(origin);
            value.setDestinationLocation(destination);
            value.setEstimatedDistanceKm(BigDecimal.valueOf(spec.distanceKm()));
            value.setEstimatedDurationMinutes(spec.durationMinutes());
            value.setStatus(RouteStatus.ACTIVE);
            routes.saveAndFlush(value);

            Map<Integer, RouteStop> existing = new HashMap<>();
            routeStops.findByRouteIdOrderByStopOrderAsc(value.getId())
                    .forEach(stop -> existing.put(stop.getStopOrder(), stop));
            List<RouteStop> stops = new ArrayList<>();
            for (int index = 0; index < spec.stops().size(); index++) {
                StopSpec stopSpec = spec.stops().get(index);
                RouteStop stop = existing.getOrDefault(index + 1, new RouteStop());
                stop.setRoute(value);
                stop.setLocation(location.get(stopSpec.location()));
                stop.setStopOrder(index + 1);
                stop.setAllowPickup(index < spec.stops().size() - 1);
                stop.setAllowDropoff(index > 0);
                stop.setEstimatedOffsetMinutes(stopSpec.offsetMinutes());
                stop.setStatus(ActiveStatus.ACTIVE);
                stops.add(routeStops.save(stop));
            }
            routeStops.flush();
            result.put(spec.key(), new RouteBundle(value, stops));
        }
        return result;
    }

    private Map<String, BusType> seedBusTypes() {
        Map<String, BusType> result = new LinkedHashMap<>();
        for (BusTypeSpec spec : List.of(
                new BusTypeSpec("LIMO22", "Limousine 22 chỗ", "Ghế limousine 1+1, một tầng", limousineSeats()),
                new BusTypeSpec("SLEEPER34", "Giường nằm 34 chỗ", "Giường nằm hai tầng, lối đi giữa", sleeperSeats()),
                new BusTypeSpec("STANDARD40", "Ghế ngồi 40 chỗ", "Ghế tiêu chuẩn 2+2, lối đi giữa", standardSeats()))) {
            BusType value = busTypes.findFirstByName(spec.name()).orElseGet(BusType::new);
            requireAtMostOne("bus type " + spec.key(), "SELECT COUNT(*) FROM bus_types WHERE name=?", spec.name());
            if (value.getId() != null) {
                if (value.getSeatCount() != spec.seats().size() || !spec.description().equals(value.getDescription())
                        || jdbc.queryForObject("SELECT COUNT(*) FROM seat_templates WHERE bus_type_id=?",
                                Long.class, value.getId()) != spec.seats().size()) throw collision("bus type " + spec.key());
                for (SeatSpec expected : spec.seats()) {
                    SeatTemplate seat = seatTemplates.findByBusTypeIdAndSeatCode(value.getId(), expected.code())
                            .orElseThrow(() -> collision("seat template " + expected.code()));
                    if (seat.getFloor() != expected.floor() || seat.getRow() != expected.row()
                            || seat.getColumn() != expected.column() || seat.getSeatType() != SeatType.STANDARD)
                        throw collision("seat template " + expected.code());
                }
                result.put(spec.key(), value);
                continue;
            }
            value.setName(spec.name());
            value.setSeatCount(spec.seats().size());
            value.setDescription(spec.description());
            value.setStatus(ActiveStatus.ACTIVE);
            busTypes.saveAndFlush(value);
            for (SeatSpec seatSpec : spec.seats()) {
                SeatTemplate seat = seatTemplates.findByBusTypeIdAndSeatCode(value.getId(), seatSpec.code())
                        .orElseGet(SeatTemplate::new);
                seat.setBusType(value);
                seat.setSeatCode(seatSpec.code());
                seat.setRow(seatSpec.row());
                seat.setColumn(seatSpec.column());
                seat.setFloor(seatSpec.floor());
                seat.setSeatType(SeatType.STANDARD);
                seat.setActive(true);
                seatTemplates.save(seat);
            }
            seatTemplates.flush();
            result.put(spec.key(), value);
        }
        return result;
    }

    private Map<String, Bus> seedBuses(Map<String, TransportOperator> operator, Map<String, BusType> type) {
        Map<String, Bus> result = new LinkedHashMap<>();
        for (BusSpec spec : List.of(
                new BusSpec("51B-770.01", "DEMO-ANPHU", "SLEEPER34", "/images/busgo/bus-sleeper.jpg"),
                new BusSpec("51B-770.02", "DEMO-ANPHU", "STANDARD40", "/images/busgo/bus-standard.jpg"),
                new BusSpec("51B-770.03", "DEMO-ANPHU", "SLEEPER34", "/images/busgo/bus-sleeper.jpg"),
                new BusSpec("50F-880.01", "DEMO-MINHTHANH", "LIMO22", "/images/busgo/bus-limousine.jpg"),
                new BusSpec("50F-880.02", "DEMO-MINHTHANH", "SLEEPER34", "/images/busgo/bus-sleeper.jpg"),
                new BusSpec("47B-660.01", "DEMO-TAYNGUYEN", "STANDARD40", "/images/busgo/bus-standard.jpg"),
                new BusSpec("47B-660.02", "DEMO-TAYNGUYEN", "LIMO22", "/images/busgo/bus-limousine.jpg"))) {
            Bus value = buses.findByLicensePlateIgnoreCase(spec.plate()).orElseGet(Bus::new);
            if (value.getId() != null) {
                if (!value.getOperator().getId().equals(operator.get(spec.operatorCode()).getId())
                        || !value.getBusType().getId().equals(type.get(spec.busTypeKey()).getId())
                        || !Objects.equals(value.getImageUrl(), spec.imageUrl())) throw collision("bus " + spec.plate());
                result.put(spec.plate(), value);
                continue;
            }
            value.setLicensePlate(spec.plate());
            value.setOperator(operator.get(spec.operatorCode()));
            value.setBusType(type.get(spec.busTypeKey()));
            value.setImageUrl(spec.imageUrl());
            value.setStatus(BusStatus.AVAILABLE);
            result.put(spec.plate(), buses.save(value));
        }
        buses.flush();
        return result;
    }

    private Map<String, OperatorRoute> seedAssociationsAndFares(
            Map<String, TransportOperator> operator, Map<String, RouteBundle> route) {
        Map<String, OperatorRoute> result = new HashMap<>();
        List<FarePlan> plans = List.of(
                plan("DEMO-ANPHU", "HCM-DALAT", 250000), plan("DEMO-ANPHU", "DALAT-HCM", 250000),
                plan("DEMO-ANPHU", "HCM-NHATRANG", 290000), plan("DEMO-ANPHU", "NHATRANG-HCM", 290000),
                plan("DEMO-MINHTHANH", "HCM-DALAT", 300000), plan("DEMO-MINHTHANH", "DALAT-HCM", 300000),
                plan("DEMO-MINHTHANH", "DANANG-HUE", 140000), plan("DEMO-MINHTHANH", "HUE-DANANG", 140000),
                plan("DEMO-TAYNGUYEN", "HCM-DALAT", 220000), plan("DEMO-TAYNGUYEN", "DALAT-HCM", 220000),
                plan("DEMO-TAYNGUYEN", "HCM-BMT", 260000), plan("DEMO-TAYNGUYEN", "BMT-HCM", 260000));
        for (FarePlan plan : plans) {
            OperatorRoute association = association(operator.get(plan.operatorCode()), route.get(plan.routeKey()).route());
            putFare(association, route.get(plan.routeKey()).stops().get(0),
                    route.get(plan.routeKey()).stops().get(1), BigDecimal.valueOf(plan.directPrice()));
            result.put(key(plan.operatorCode(), plan.routeKey()), association);
        }

        RouteBundle coastal = route.get("COASTAL");
        OperatorRoute coastalAssociation = association(operator.get("DEMO-ANPHU"), coastal.route());
        long[][] prices = {
                {0, 1, 290000}, {1, 2, 320000}, {2, 3, 140000},
                {0, 2, 560000}, {1, 3, 410000}, {0, 3, 650000}
        };
        for (long[] price : prices) {
            putFare(coastalAssociation, coastal.stops().get((int) price[0]),
                    coastal.stops().get((int) price[1]), BigDecimal.valueOf(price[2]));
        }
        result.put(key("DEMO-ANPHU", "COASTAL"), coastalAssociation);
        return result;
    }

    private OperatorRoute association(TransportOperator operator, Route route) {
        OperatorRoute value = operatorRoutes.findByOperatorIdAndRouteId(operator.getId(), route.getId())
                .orElseGet(OperatorRoute::new);
        if (value.getId() != null) return value;
        value.setOperator(operator);
        value.setRoute(route);
        value.setStatus(ActiveStatus.ACTIVE);
        return operatorRoutes.saveAndFlush(value);
    }

    private void putFare(OperatorRoute operatorRoute, RouteStop from, RouteStop to, BigDecimal price) {
        OperatorRouteFare value = fares.findFirstByOperatorRouteIdAndFromRouteStopIdAndToRouteStopId(
                operatorRoute.getId(), from.getId(), to.getId()).orElseGet(OperatorRouteFare::new);
        requireAtMostOne("fare", "SELECT COUNT(*) FROM operator_route_fares WHERE operator_route_id=? AND from_route_stop_id=? AND to_route_stop_id=?",
                operatorRoute.getId(), from.getId(), to.getId());
        if (value.getId() != null) return;
        value.setOperatorRoute(operatorRoute);
        value.setFromRouteStop(from);
        value.setToRouteStop(to);
        value.setPrice(price);
        value.setStatus(ActiveStatus.ACTIVE);
        fares.saveAndFlush(value);
    }

    private void applyAvailability(Long tripId, int blockedSeats, boolean segmentReuseDemo) {
        if (blockedSeats > 0) {
            jdbc.update("""
                    UPDATE trip_seat_segment_inventory i
                    JOIN trip_seats seat ON seat.id=i.trip_seat_id
                    JOIN trip_segments segment ON segment.id=i.trip_segment_id
                    SET i.status='BLOCKED', i.updated_at=UTC_TIMESTAMP(6), i.version=i.version+1
                    WHERE seat.trip_id=? AND segment.segment_order=1
                      AND seat.id IN (SELECT id FROM (SELECT id FROM trip_seats
                          WHERE trip_id=? ORDER BY floor_no,row_no,column_no LIMIT ?) selected)
                      AND i.status='AVAILABLE' AND i.booking_item_id IS NULL
                    """, tripId, tripId, blockedSeats);
        }
        if (segmentReuseDemo) {
            jdbc.update("""
                    UPDATE trip_seat_segment_inventory i
                    JOIN trip_seats seat ON seat.id=i.trip_seat_id
                    JOIN trip_segments segment ON segment.id=i.trip_segment_id
                    SET i.status='BLOCKED', i.updated_at=UTC_TIMESTAMP(6), i.version=i.version+1
                    WHERE seat.trip_id=? AND ((seat.seat_code='L01' AND segment.segment_order=1)
                        OR (seat.seat_code='L02' AND segment.segment_order=2))
                      AND i.status='AVAILABLE' AND i.booking_item_id IS NULL
                    """, tripId);
        }
    }

    private boolean hasUsableFares(OperatorRoute association, RouteBundle source) {
        for (int from = 0; from < source.stops().size() - 1; from++) {
            for (int to = from + 1; to < source.stops().size(); to++) {
                var fare = fares.findFirstByOperatorRouteIdAndFromRouteStopIdAndToRouteStopId(
                        association.getId(), source.stops().get(from).getId(), source.stops().get(to).getId());
                if (fare.isEmpty() || fare.get().getStatus() != ActiveStatus.ACTIVE
                        || fare.get().getPrice().signum() <= 0) return false;
            }
        }
        return true;
    }

    private void requireAtMostOne(String key, String sql, Object... args) {
        if (jdbc.queryForObject(sql, Long.class, args) > 1) throw collision(key + " (ambiguous duplicates)");
    }

    private static IllegalStateException collision(String key) {
        return new IllegalStateException("Demo fixture collision: " + key
                + ". Existing data will not be repaired or overwritten. Use dev without demo to inspect it.");
    }

    static List<SeatSpec> limousineSeats() {
        List<SeatSpec> seats = new ArrayList<>();
        int number = 1;
        for (int row = 1; row <= 11; row++) for (int column : List.of(1, 3))
            seats.add(new SeatSpec("A%02d".formatted(number++), row, column, 1));
        return seats;
    }

    static List<SeatSpec> sleeperSeats() {
        List<SeatSpec> seats = new ArrayList<>();
        for (int floor = 1; floor <= 2; floor++) {
            int number = 1;
            for (int row = 1; row <= 5; row++) for (int column : List.of(1, 3, 5))
                seats.add(new SeatSpec((floor == 1 ? "L" : "U") + "%02d".formatted(number++), row, column, floor));
            for (int column : List.of(1, 5))
                seats.add(new SeatSpec((floor == 1 ? "L" : "U") + "%02d".formatted(number++), 6, column, floor));
        }
        return seats;
    }

    static List<SeatSpec> standardSeats() {
        List<SeatSpec> seats = new ArrayList<>();
        int number = 1;
        for (int row = 1; row <= 10; row++) for (int column : List.of(1, 2, 4, 5))
            seats.add(new SeatSpec("S%02d".formatted(number++), row, column, 1));
        return seats;
    }

    private static List<TripSpec> tripSpecs() {
        return List.of(
                new TripSpec("DEMO-ANPHU", "HCM-DALAT", "51B-770.01", LocalTime.of(6, 30), 4),
                new TripSpec("DEMO-MINHTHANH", "HCM-DALAT", "50F-880.01", LocalTime.of(8, 0), 2),
                new TripSpec("DEMO-TAYNGUYEN", "HCM-DALAT", "47B-660.01", LocalTime.of(9, 30), 8),
                new TripSpec("DEMO-ANPHU", "HCM-DALAT", "51B-770.02", LocalTime.of(13, 0), 6),
                new TripSpec("DEMO-MINHTHANH", "HCM-DALAT", "50F-880.02", LocalTime.of(18, 30), 7),
                new TripSpec("DEMO-TAYNGUYEN", "HCM-DALAT", "47B-660.02", LocalTime.of(22, 0), 5),
                new TripSpec("DEMO-ANPHU", "COASTAL", "51B-770.03", LocalTime.of(6, 0), 0));
    }

    private static RouteSpec direct(String key, String name, String origin, String destination,
            int distanceKm, int durationMinutes) {
        return new RouteSpec(key, name, origin, destination, distanceKm, durationMinutes,
                List.of(new StopSpec(origin, 0), new StopSpec(destination, durationMinutes)));
    }

    private static FarePlan plan(String operator, String route, long price) {
        return new FarePlan(operator, route, price);
    }

    private static String key(String operator, String route) { return operator + "|" + route; }

    public record SeedResult(LocalDate searchDate, Location pickup, Location dropoff,
            int createdTrips, int reusedTrips, int skippedTrips) {}
    record SeatSpec(String code, int row, int column, int floor) {}
    private record OperatorSpec(String code, String name, String phone, String email, String address) {}
    private record LocationSpec(String key, String name, String province, String district, String address) {}
    private record StopSpec(String location, int offsetMinutes) {}
    private record RouteSpec(String key, String name, String origin, String destination,
            int distanceKm, int durationMinutes, List<StopSpec> stops) {}
    private record RouteBundle(Route route, List<RouteStop> stops) {}
    private record BusTypeSpec(String key, String name, String description, List<SeatSpec> seats) {}
    private record BusSpec(String plate, String operatorCode, String busTypeKey, String imageUrl) {}
    private record FarePlan(String operatorCode, String routeKey, long directPrice) {}
    private record TripSpec(String operatorCode, String routeKey, String plate,
            LocalTime time, int blockedSeats) {}
}
