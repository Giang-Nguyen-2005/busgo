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
import com.busgo.operator.entity.TransportOperator;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit-profile, repeatable portfolio data. Never active in production by default. */
@Component
@Profile("demo & !prod & !production")
public class DemoDataSeeder implements ApplicationRunner {
    public static final List<String> OPERATOR_CODES = List.of("DEMO-ANPHU", "DEMO-MINHTHANH", "DEMO-TAYNGUYEN");
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
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean reset;

    public DemoDataSeeder(TransportOperatorRepository operators, LocationRepository locations,
            RouteRepository routes, RouteStopRepository routeStops,
            OperatorRouteRepository operatorRoutes, OperatorRouteFareRepository fares,
            BusTypeRepository busTypes, SeatTemplateRepository seatTemplates,
            BusRepository buses, TripRepository trips, TripAggregateCreator aggregateCreator,
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
        this.jdbc = jdbc;
        this.clock = clock;
        this.reset = reset;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (reset) resetUnbookedDemoTrips();
        SeedResult result = seed();
        log.info("BusGo demo data ready: search {} -> {} on {} ({} trips created, {} reused)",
                result.pickup().getName(), result.dropoff().getName(), result.searchDate(),
                result.createdTrips(), result.reusedTrips());
    }

    SeedResult seed() {
        Map<String, TransportOperator> operator = seedOperators();
        Map<String, Location> location = seedLocations();
        Map<String, RouteBundle> route = seedRoutes(location);
        Map<String, BusType> type = seedBusTypes();
        Map<String, Bus> bus = seedBuses(operator, type);
        Map<String, OperatorRoute> association = seedAssociationsAndFares(operator, route);

        LocalDate searchDate = LocalDate.now(clock.withZone(BUSINESS_ZONE)).plusDays(1);
        int created = 0;
        int reused = 0;
        for (TripSpec spec : tripSpecs()) {
            OperatorRoute operatorRoute = association.get(key(spec.operatorCode(), spec.routeKey()));
            Bus assignedBus = bus.get(spec.plate());
            LocalDateTime departure = businessTime(searchDate.plusDays(spec.dayOffset()), spec.time());
            Trip trip = trips.findByOperatorRouteIdAndBusIdAndDepartureTime(
                    operatorRoute.getId(), assignedBus.getId(), departure).orElse(null);
            if (trip == null) {
                trip = aggregateCreator.create(operatorRoute.getOperator().getId(), operatorRoute.getId(),
                        assignedBus.getId(), departure).trip();
                created++;
            } else {
                reused++;
            }
            applyAvailability(trip.getId(), spec.blockedSeats(), spec.routeKey().equals("COASTAL"));
        }
        return new SeedResult(searchDate, location.get("HCM"), location.get("DALAT"), created, reused);
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
                new BusSpec("51B-770.01", "DEMO-ANPHU", "SLEEPER34"),
                new BusSpec("51B-770.02", "DEMO-ANPHU", "STANDARD40"),
                new BusSpec("50F-880.01", "DEMO-MINHTHANH", "LIMO22"),
                new BusSpec("50F-880.02", "DEMO-MINHTHANH", "SLEEPER34"),
                new BusSpec("47B-660.01", "DEMO-TAYNGUYEN", "STANDARD40"),
                new BusSpec("47B-660.02", "DEMO-TAYNGUYEN", "LIMO22"))) {
            Bus value = buses.findByLicensePlateIgnoreCase(spec.plate()).orElseGet(Bus::new);
            value.setLicensePlate(spec.plate());
            value.setOperator(operator.get(spec.operatorCode()));
            value.setBusType(type.get(spec.busTypeKey()));
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
        value.setOperator(operator);
        value.setRoute(route);
        value.setStatus(ActiveStatus.ACTIVE);
        return operatorRoutes.saveAndFlush(value);
    }

    private void putFare(OperatorRoute operatorRoute, RouteStop from, RouteStop to, BigDecimal price) {
        OperatorRouteFare value = fares.findFirstByOperatorRouteIdAndFromRouteStopIdAndToRouteStopId(
                operatorRoute.getId(), from.getId(), to.getId()).orElseGet(OperatorRouteFare::new);
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

    private void resetUnbookedDemoTrips() {
        String marks = String.join(",", java.util.Collections.nCopies(OPERATOR_CODES.size(), "?"));
        List<Long> ids = jdbc.queryForList("""
                SELECT t.id FROM trips t
                JOIN operator_routes operator_route ON operator_route.id=t.operator_route_id
                JOIN transport_operators operator ON operator.id=operator_route.operator_id
                WHERE operator.code IN (%s)
                  AND NOT EXISTS (SELECT 1 FROM bookings booking WHERE booking.trip_id=t.id)
                """.formatted(marks), Long.class, OPERATOR_CODES.toArray());
        for (Long id : ids) {
            jdbc.update("DELETE i FROM trip_seat_segment_inventory i JOIN trip_seats s ON s.id=i.trip_seat_id WHERE s.trip_id=?", id);
            jdbc.update("DELETE FROM trip_segments WHERE trip_id=?", id);
            jdbc.update("DELETE FROM trip_seats WHERE trip_id=?", id);
            jdbc.update("DELETE FROM trip_stops WHERE trip_id=?", id);
            jdbc.update("DELETE FROM trips WHERE id=?", id);
        }
        log.info("Reset {} unbooked demo trips; booked demo history was preserved", ids.size());
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
                new TripSpec("DEMO-ANPHU", "HCM-DALAT", "51B-770.01", 0, LocalTime.of(6, 30), 4),
                new TripSpec("DEMO-MINHTHANH", "HCM-DALAT", "50F-880.01", 0, LocalTime.of(8, 0), 2),
                new TripSpec("DEMO-TAYNGUYEN", "HCM-DALAT", "47B-660.01", 0, LocalTime.of(9, 30), 8),
                new TripSpec("DEMO-ANPHU", "HCM-DALAT", "51B-770.02", 0, LocalTime.of(13, 0), 6),
                new TripSpec("DEMO-MINHTHANH", "HCM-DALAT", "50F-880.02", 0, LocalTime.of(18, 30), 7),
                new TripSpec("DEMO-TAYNGUYEN", "HCM-DALAT", "47B-660.02", 0, LocalTime.of(22, 0), 5),
                new TripSpec("DEMO-ANPHU", "HCM-NHATRANG", "51B-770.02", 1, LocalTime.of(6, 0), 5),
                new TripSpec("DEMO-ANPHU", "NHATRANG-HCM", "51B-770.02", 1, LocalTime.of(18, 0), 3),
                new TripSpec("DEMO-TAYNGUYEN", "HCM-BMT", "47B-660.01", 1, LocalTime.of(7, 0), 6),
                new TripSpec("DEMO-TAYNGUYEN", "BMT-HCM", "47B-660.01", 1, LocalTime.of(18, 0), 4),
                new TripSpec("DEMO-MINHTHANH", "DANANG-HUE", "50F-880.01", 1, LocalTime.of(6, 0), 2),
                new TripSpec("DEMO-MINHTHANH", "HUE-DANANG", "50F-880.01", 1, LocalTime.of(12, 0), 1),
                new TripSpec("DEMO-ANPHU", "COASTAL", "51B-770.01", 2, LocalTime.of(6, 0), 0));
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

    record SeedResult(LocalDate searchDate, Location pickup, Location dropoff, int createdTrips, int reusedTrips) {}
    record SeatSpec(String code, int row, int column, int floor) {}
    private record OperatorSpec(String code, String name, String phone, String email, String address) {}
    private record LocationSpec(String key, String name, String province, String district, String address) {}
    private record StopSpec(String location, int offsetMinutes) {}
    private record RouteSpec(String key, String name, String origin, String destination,
            int distanceKm, int durationMinutes, List<StopSpec> stops) {}
    private record RouteBundle(Route route, List<RouteStop> stops) {}
    private record BusTypeSpec(String key, String name, String description, List<SeatSpec> seats) {}
    private record BusSpec(String plate, String operatorCode, String busTypeKey) {}
    private record FarePlan(String operatorCode, String routeKey, long directPrice) {}
    private record TripSpec(String operatorCode, String routeKey, String plate,
            int dayOffset, LocalTime time, int blockedSeats) {}
}
