package com.busgo.demo.m31;

import static com.busgo.common.time.BusGoTime.*;
import static com.busgo.demo.m31.M31Schedule.*;
import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.security.CurrentUser;
import com.busgo.fleet.*;
import com.busgo.fleet.entity.*;
import com.busgo.fleet.repository.*;
import com.busgo.operator.*;
import com.busgo.operator.entity.*;
import com.busgo.operator.repository.*;
import com.busgo.route.*;
import com.busgo.route.entity.*;
import com.busgo.route.repository.*;
import com.busgo.trip.TripAggregateCreator;
import com.busgo.trip.entity.*;
import com.busgo.trip.repository.*;
import com.busgo.user.entity.RoleCode;
import com.busgo.user.repository.UserRoleRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** One atomic rotation batch, at most 28 complete aggregates. Existing rows are never repaired. */
@Service
@Profile("dev & !prod & !production")
public class M31ScheduleBatchService {
    private final M31CatalogueService catalogue;
    private final TransportOperatorRepository operators;
    private final OperatorStaffRepository staff;
    private final UserRoleRepository userRoles;
    private final BusRepository buses;
    private final BusTypeService layouts;
    private final RouteRepository routes;
    private final RouteStopRepository stops;
    private final RouteDefinitionService definitions;
    private final OperatorRouteRepository associations;
    private final OperatorRouteFareRepository fares;
    private final OperatorRouteService routeService;
    private final OperatorRouteFareService fareService;
    private final OperatorCommerceGuard commerce;
    private final TripRepository trips;
    private final TripStopSnapshotRepository snapshots;
    private final TripSeatRepository seats;
    private final FleetMaintenanceGuard maintenance;
    private final TripAggregateCreator creator;
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final Clock clock;
    public M31ScheduleBatchService(M31CatalogueService catalogue,TransportOperatorRepository operators,OperatorStaffRepository staff,
            UserRoleRepository userRoles,BusRepository buses,BusTypeService layouts,RouteRepository routes,RouteStopRepository stops,
            RouteDefinitionService definitions,OperatorRouteRepository associations,OperatorRouteFareRepository fares,
            OperatorRouteService routeService,OperatorRouteFareService fareService,OperatorCommerceGuard commerce,
            TripRepository trips,TripStopSnapshotRepository snapshots,TripSeatRepository seats,FleetMaintenanceGuard maintenance,
            TripAggregateCreator creator,JdbcTemplate jdbc,EntityManager em,Clock clock) {
        this.catalogue=catalogue; this.operators=operators; this.staff=staff; this.userRoles=userRoles; this.buses=buses;
        this.layouts=layouts; this.routes=routes; this.stops=stops; this.definitions=definitions; this.associations=associations;
        this.fares=fares; this.routeService=routeService; this.fareService=fareService; this.commerce=commerce;
        this.trips=trips; this.snapshots=snapshots; this.seats=seats; this.maintenance=maintenance; this.creator=creator;
        this.jdbc=jdbc; this.em=em; this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED)
    public Batch dryRun(LocalDate anchor,int days,String plate,boolean placement) { return run(anchor,days,plate,placement,false); }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Batch apply(LocalDate anchor,int days,String plate,boolean placement) { return run(anchor,days,plate,placement,true); }

    private record Supply(M31Catalogue.Direction spec,Route route,List<RouteStop> stops,OperatorRoute association,
                          BigDecimal price,boolean newFare) {}
    private Batch run(LocalDate anchor,int days,String plate,boolean placement,boolean apply) {
        var legs=M31Schedule.legs(anchor,days,plate);
        if(legs.isEmpty()) throw new IllegalArgumentException("Unknown/non-rotating bus");
        var guard=catalogue.dryRun(M31Catalogue.Batch.FULL);
        if(guard.entries().stream().anyMatch(e->!e.action().equals("REUSE"))) throw new IllegalStateException("M31.2 baseline conflict; no automatic catalogue repair");
        M31Fleet.validate();
        if(apply) jdbc.queryForObject("SELECT id FROM roles WHERE code='CUSTOMER' FOR UPDATE",Long.class);
        var manifest=M31Fleet.vehicles().stream().filter(v->v.plate().equals(plate)).findFirst().orElseThrow();
        var op=operators.findByCode(manifest.owner()).orElseThrow();
        var bus=buses.findByLicensePlateIgnoreCase(plate).orElse(null);
        var items=new ArrayList<Item>();
        if(bus==null || !bus.getOperator().getId().equals(op.getId())) return blocked(legs,manifest,placement,items,"BUS_OWNER_OR_IDENTITY_CONFLICT");
        if(apply) {
            bus=buses.findOwnedByIdForUpdate(bus.getId(),op.getId()).orElseThrow(); em.refresh(bus);
            commerce.requireActive(op.getId()); em.refresh(op); // Bus first, then operator: production lock order.
        }
        if(op.getStatus()!=OperatorStatus.ACTIVE || bus.getStatus()!=BusStatus.AVAILABLE || bus.getDeletedAt()!=null)
            return blocked(legs,manifest,placement,items,"OPERATOR_OR_BUS_UNAVAILABLE");
        if(!bus.getBusType().getName().equals(M31Fleet.types().get(manifest.type())))
            return blocked(legs,manifest,placement,items,"BUS_TYPE_IDENTITY_CONFLICT");
        BusType type;
        try { type=layouts.requireUsable(bus.getBusType().getId()); }
        catch(com.busgo.common.exception.BusinessException e) { return blocked(legs,manifest,placement,items,"INVALID_ACTIVE_LAYOUT"); }
        var actor=admin(op.getId());
        if(actor==null) return blocked(legs,manifest,placement,items,"DEDICATED_ACTIVE_ADMIN_REQUIRED");
        var supplies=new LinkedHashMap<String,Supply>();
        String conflict=null;
        for(var leg:legs) if(!supplies.containsKey(leg.route())) {
            try { supplies.put(leg.route(),supply(op,leg,items)); }
            catch(PreflightConflict e) { conflict=e.getMessage(); break; }
        }
        if(conflict!=null) return blocked(legs,manifest,placement,items,conflict);
        var history=trips.findByBusIdOrderByDepartureTimeAsc(bus.getId());
        var reused=new HashMap<String,Trip>();
        for(var leg:legs) {
            var departure=utc(leg.departure().atZone(BUSINESS_ZONE));
            var candidates=history.stream().filter(t->t.getDepartureTime().equals(departure)).toList();
            if(!candidates.isEmpty()) {
                if(candidates.size()!=1 || !exact(candidates.get(0),leg,supplies.get(leg.route()),type.getSeatCount()))
                    return blocked(legs,manifest,placement,items,"EXISTING_SLOT_OR_AGGREGATE_CONFLICT: "+key(leg));
                reused.put(key(leg),candidates.get(0));
            } else if(!departure.isAfter(utc(clock.instant()))) return blocked(legs,manifest,placement,items,"MISSING_PAST_SLOT: "+key(leg));
            if(!reused.containsKey(key(leg)) && !maintenance.warning(bus.getId(),departure,utc(leg.arrival().atZone(BUSINESS_ZONE))).isEmpty())
                return blocked(legs,manifest,placement,items,"MAINTENANCE_CONFLICT: "+key(leg));
        }
        if(reused.size()!=legs.size()) {
            if(!placement) return blocked(legs,manifest,false,items,"EXPLICIT_INITIAL_PLACEMENT_ACK_REQUIRED");
            if(reused.values().stream().anyMatch(t->t.getStatus()==TripStatus.CANCELLED))
                return blocked(legs,manifest,true,items,"CANCELLED_PREDECESSOR: missing slots will not restore a cancelled circulation");
            var ids=reused.values().stream().map(Trip::getId).collect(java.util.stream.Collectors.toSet());
            var first=utc(legs.get(0).departure().atZone(BUSINESS_ZONE));
            var end=utc(legs.get(legs.size()-1).arrival().atZone(BUSINESS_ZONE));
            Trip previous=null,next=null;
            for(var t:history) {
                if(ids.contains(t.getId()) || t.getStatus()==TripStatus.CANCELLED) continue;
                if(t.getDepartureTime().isBefore(end.plusMinutes(90)) && t.getEstimatedArrivalTime().isAfter(first.minusMinutes(90)))
                    return blocked(legs,manifest,true,items,"EXISTING_BUS_SCHEDULE_CONFLICT: trip "+t.getId());
                if(!t.getEstimatedArrivalTime().isAfter(first) && (previous==null || t.getEstimatedArrivalTime().isAfter(previous.getEstimatedArrivalTime()))) previous=t;
                if(!t.getDepartureTime().isBefore(end) && (next==null || t.getDepartureTime().isBefore(next.getDepartureTime()))) next=t;
            }
            // Position between legacy and this fixed window is an explicit demo assumption, never instantaneous.
            if(previous!=null) {
                var actual=snapshots.findByTripIdOrderByStopOrderAsc(previous.getId());
                var origin=supplies.get(legs.get(0).route()).route().getOriginLocation().getId();
                if(actual.isEmpty() || (!actual.get(actual.size()-1).getLocation().getId().equals(origin)
                        && previous.getEstimatedArrivalTime().plusHours(24).isAfter(first)))
                    return blocked(legs,manifest,true,items,"INITIAL_POSITION_REQUIRES_24H_REPOSITION_GAP");
            }
            if(next!=null) {
                var actual=snapshots.findByTripIdOrderByStopOrderAsc(next.getId());
                var destination=supplies.get(legs.get(legs.size()-1).route()).route().getDestinationLocation().getId();
                if(actual.isEmpty() || !actual.get(0).getLocation().getId().equals(destination))
                    return blocked(legs,manifest,true,items,"FUTURE_MANUAL_TRIP_POSITION_CONFLICT");
            }
        }
        var results=new ArrayList<Departure>();
        if(apply) {
            var old=SecurityContextHolder.getContext(); var context=SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor,null,List.of(new SimpleGrantedAuthority("ROLE_OPERATOR_ADMIN"))));
            try {
                SecurityContextHolder.setContext(context);
                for(var entry:new ArrayList<>(supplies.entrySet())) {
                    var s=entry.getValue(); OperatorRoute a=s.association();
                    if(a==null) {
                        var attached=routeService.attach(actor,new RouteDtos.AttachRouteRequest(s.route().getId()));
                        a=associations.findById(attached.id()).orElseThrow();
                    }
                    if(s.newFare()) fareService.replace(actor,a.getId(),new RouteDtos.ReplaceFaresRequest(List.of(
                        new RouteDtos.FareInput(s.stops().get(0).getId(),s.stops().get(1).getId(),s.price()))));
                    supplies.put(entry.getKey(),new Supply(s.spec(),s.route(),s.stops(),a,s.price(),false));
                }
                for(var leg:legs) {
                    Trip existing=reused.get(key(leg)); Long id;
                    if(existing!=null) id=existing.getId();
                    else id=creator.create(op.getId(),supplies.get(leg.route()).association().getId(),bus.getId(),utc(leg.departure().atZone(BUSINESS_ZONE))).trip().getId();
                    results.add(new Departure(key(leg),existing==null?"CREATED":"REUSE",id,"Existing inventory/lifecycle preserved on reuse",leg,manifest.type(),type.getSeatCount(),type.getSeatCount(),supplies.get(leg.route()).price()));
                }
            } finally { SecurityContextHolder.setContext(old); }
            items.replaceAll(e->e.action().equals("CREATE")?new Item(e.kind(),e.key(),"CREATED",resolveItemId(e,op,supplies),e.reason(),e.price()):e);
        } else for(var leg:legs) results.add(new Departure(key(leg),reused.containsKey(key(leg))?"REUSE":"CREATE",
                reused.containsKey(key(leg))?reused.get(key(leg)).getId():null,"Simulated VND gross fare; initial base is explicit",leg,manifest.type(),type.getSeatCount(),type.getSeatCount(),supplies.get(leg.route()).price()));
        return new Batch(plate,legs.get(0).from(),placement,List.copyOf(items),List.copyOf(results),false);
    }
    private Long resolveItemId(Item e,TransportOperator op,Map<String,Supply> supplies) {
        var s=supplies.get(e.key().substring(e.key().indexOf(':')+1));
        if(e.kind().equals("ASSOCIATION")) return s.association().getId();
        return fares.findByOperatorRouteIdOrderByFromRouteStopStopOrderAscToRouteStopStopOrderAsc(s.association().getId()).get(0).getId();
    }
    private Supply supply(TransportOperator op,M31Fleet.Leg leg,List<Item> items) {
        var spec=M31Catalogue.routes(M31Catalogue.Batch.FULL).stream().filter(r->r.key().equals(leg.route())).findFirst().orElseThrow();
        var candidates=routes.findCatalogueCandidates(spec.name(),-1L,-1L);
        if(candidates.size()!=1) throw new PreflightConflict("ROUTE_IDENTITY_CONFLICT: "+spec.key());
        Route route;
        try { route=definitions.requireUsable(candidates.get(0).getId()); }
        catch(com.busgo.common.exception.BusinessException e) { throw new PreflightConflict("ROUTE_NOT_USABLE: "+spec.key()); }
        var source=stops.findByRouteIdOrderByStopOrderAsc(route.getId());
        if(source.size()!=2 || !source.get(0).isAllowPickup() || !source.get(1).isAllowDropoff()
                || source.stream().anyMatch(s->s.getStatus()!=ActiveStatus.ACTIVE || s.getLocation().getStatus()!=ActiveStatus.ACTIVE))
            throw new PreflightConflict("DIRECT_STOPS_NOT_USABLE: "+spec.key());
        var price=M31Schedule.price(op.getCode(),spec.key()); String key=op.getCode()+":"+spec.key();
        var a=associations.findByOperatorIdAndRouteId(op.getId(),route.getId()).orElse(null);
        if(a==null && price.legacy()) throw new PreflightConflict("MISSING_LEGACY_ASSOCIATION: "+key);
        if(a!=null && a.getStatus()!=ActiveStatus.ACTIVE) {
            items.add(new Item("ASSOCIATION",key,"CONFLICT",a.getId(),"Inactive association preserved",null));
            throw new PreflightConflict("INACTIVE_ASSOCIATION: "+key);
        }
        items.add(new Item("ASSOCIATION",key,a==null?"CREATE":"REUSE",a==null?null:a.getId(),"Owner/route pair; no reactivation",null));
        var existing=a==null?List.<OperatorRouteFare>of():fares.findByOperatorRouteIdOrderByFromRouteStopStopOrderAscToRouteStopStopOrderAsc(a.getId());
        if(existing.isEmpty() && !price.legacy()) {
            items.add(new Item("FARE",key,"CREATE",null,"Simulated VND; one exact forward pair",price.amount()));
            return new Supply(spec,route,source,a,price.amount(),true);
        }
        if(existing.size()!=1 || existing.get(0).getStatus()!=ActiveStatus.ACTIVE || existing.get(0).getPrice().signum()<=0
                || !existing.get(0).getFromRouteStop().getId().equals(source.get(0).getId()) || !existing.get(0).getToRouteStop().getId().equals(source.get(1).getId())) {
            items.add(new Item("FARE",key,"CONFLICT",null,"Missing/duplicate/inactive/wrong-stop existing fare preserved",null));
            throw new PreflightConflict("FARE_IDENTITY_CONFLICT: "+key);
        }
        var fare=existing.get(0);
        items.add(new Item("FARE",key,"REUSE",fare.getId(),"Actual active price preserved, including user changes",fare.getPrice()));
        return new Supply(spec,route,source,a,fare.getPrice(),false);
    }
    private boolean exact(Trip t,M31Fleet.Leg leg,Supply s,int count) {
        if(s.association()==null || !t.getOperatorRoute().getId().equals(s.association().getId())
                || !t.getEstimatedArrivalTime().equals(utc(leg.arrival().atZone(BUSINESS_ZONE)))) return false;
        var source=snapshots.findByTripIdOrderByStopOrderAsc(t.getId());
        if(source.size()!=2 || !source.get(0).getLocation().getId().equals(s.route().getOriginLocation().getId())
                || !source.get(1).getLocation().getId().equals(s.route().getDestinationLocation().getId())
                || seats.countByTripId(t.getId())!=count) return false;
        return jdbc.queryForObject("SELECT COUNT(*) FROM trip_segments WHERE trip_id=?",Long.class,t.getId())==1
            && jdbc.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory i JOIN trip_seats s ON s.id=i.trip_seat_id JOIN trip_segments g ON g.id=i.trip_segment_id WHERE s.trip_id=? AND g.trip_id=?",Long.class,t.getId(),t.getId())==count;
    }
    private CurrentUser admin(Long owner) {
        return staff.findAll().stream().filter(s->s.getOperator().getId().equals(owner) && s.getStatus()==ActiveStatus.ACTIVE
                && s.getUser().getStatus()==com.busgo.user.entity.UserStatus.ACTIVE && s.getUser().getDeletedAt()==null)
            .filter(s->staff.findByUserId(s.getUser().getId()).size()==1 && userRoles.findRoleCodesByUserId(s.getUser().getId()).equals(List.of(RoleCode.OPERATOR_ADMIN)))
            .map(s->new CurrentUser(s.getUser().getId(),List.of(RoleCode.OPERATOR_ADMIN))).findFirst().orElse(null);
    }
    private Batch blocked(List<M31Fleet.Leg> legs,M31Fleet.Vehicle v,boolean placement,List<Item> items,String reason) {
        var events=items.stream().map(e->e.action().equals("CREATE")?new Item(e.kind(),e.key(),"SKIP",null,reason,e.price()):e).toList();
        return new Batch(v.plate(),legs.get(0).from(),placement,events,legs.stream().map(l->new Departure(key(l),"SKIP",null,reason,l,v.type(),0,0,null)).toList(),false);
    }
    private static class PreflightConflict extends RuntimeException { PreflightConflict(String reason) { super(reason); } }
}
