package com.busgo.demo.m31;

import com.busgo.common.entity.ActiveStatus;
import com.busgo.location.entity.Location;
import com.busgo.location.repository.LocationRepository;
import com.busgo.route.RouteDefinitionService;
import com.busgo.route.entity.*;
import com.busgo.route.repository.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Small, create-only catalogue transaction. No commerce, operator, fare or trip writes. */
@Service
@Profile("dev & !prod & !production")
public class M31CatalogueService {
    public record Entry(String kind, String key, String action, Long id, String reason) {}
    public record Report(String manifest, M31Catalogue.Batch batch, boolean dryRun, String schema,
                         List<Entry> entries) {
        public long count(String kind, String action) {
            return entries.stream().filter(e -> e.kind().equals(kind) && e.action().equals(action)).count();
        }
    }
    private final LocationRepository locations;
    private final RouteRepository routes;
    private final RouteStopRepository stops;
    private final RouteDefinitionService definitions;
    private final JdbcTemplate jdbc;
    private final Environment environment;
    private final String verifiedSchema;

    public M31CatalogueService(LocationRepository locations, RouteRepository routes,
            RouteStopRepository stops, RouteDefinitionService definitions, JdbcTemplate jdbc,
            Environment environment, @Value("${busgo.m31.catalogue.verified-schema:}") String verifiedSchema) {
        this.locations=locations; this.routes=routes; this.stops=stops; this.definitions=definitions;
        this.jdbc=jdbc; this.environment=environment; this.verifiedSchema=verifiedSchema;
    }
    @Transactional(readOnly=true, isolation=Isolation.READ_COMMITTED)
    public Report dryRun(M31Catalogue.Batch batch) { return execute(batch,false); }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Report apply(M31Catalogue.Batch batch) {
        verifyTarget();
        // Serialize cooperating catalogue runs even though legacy natural keys are not unique.
        // Lock only a stable system-role row; never change it or lock commerce records.
        jdbc.queryForObject("SELECT id FROM roles WHERE code='CUSTOMER' FOR UPDATE",Long.class);
        return execute(batch,true);
    }
    private String verifyTarget() {
        if (!environment.matchesProfiles("dev & !prod & !production")
                || environment.matchesProfiles("demo"))
            throw new IllegalStateException("M31 requires dev without demo/prod/production; legacy seed must not run concurrently");
        String schema=jdbc.queryForObject("SELECT DATABASE()",String.class);
        String url=jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>) c -> c.getMetaData().getURL());
        if (schema==null || !schema.matches("busgo_m312_[a-z0-9_]+") || !schema.equals(verifiedSchema)
                || url==null || !url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/.*"))
            throw new IllegalStateException("M31 target denied: verify isolated loopback schema and set busgo.m31.catalogue.verified-schema");
        var versions=jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success=TRUE AND version IS NOT NULL",String.class);
        if (versions.size()!=28 || !versions.contains("28")
                || jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=FALSE",Long.class)!=0)
            throw new IllegalStateException("M31 requires successful Flyway V1–V28; do not repair/reset automatically");
        return schema;
    }
    private Report execute(M31Catalogue.Batch batch, boolean apply) {
        String schema=verifyTarget();
        M31Catalogue.validate(batch);
        var entries=new ArrayList<Entry>();
        var resolved=new HashMap<String,Location>();
        var pending=new HashSet<String>();
        for (var p:M31Catalogue.places(batch)) {
            var candidates=locations.findByName(p.name());
            if(candidates.isEmpty()) {
                if(p.legacy()) { entries.add(new Entry("LOCATION",p.key(),"CONFLICT",null,"Legacy baseline missing; prepare separately")); continue; }
                if(apply) {
                    Location l=new Location(); l.setName(p.name()); l.setStatus(ActiveStatus.ACTIVE);
                    l=locations.saveAndFlush(l); resolved.put(p.key(),l);
                    entries.add(new Entry("LOCATION",p.key(),"CREATED",l.getId(),"SIMULATED; no verified address/coordinates"));
                } else { pending.add(p.key()); entries.add(new Entry("LOCATION",p.key(),"CREATE",null,"SIMULATED")); }
            } else if(candidates.size()==1 && matches(candidates.get(0),p)) {
                var l=candidates.get(0); resolved.put(p.key(),l);
                entries.add(new Entry("LOCATION",p.key(),"REUSE",l.getId(),"Preserved, including current status"));
            } else entries.add(new Entry("LOCATION",p.key(),"CONFLICT",null,"Ambiguous name or changed identifying fields; preserved"));
        }
        for(var r:M31Catalogue.routes(batch)) {
            Location from=resolved.get(r.from()),to=resolved.get(r.to());
            if((from==null && !pending.contains(r.from())) || (to==null && !pending.contains(r.to()))) {
                entries.add(new Entry("ROUTE",r.key(),"CONFLICT",null,"Unresolved location dependency")); continue;
            }
            // Query by name too when an endpoint is planned but has not yet received a generated ID.
            var candidates=routes.findCatalogueCandidates(r.name(),from==null?-1L:from.getId(),to==null?-1L:to.getId());
            if(!candidates.isEmpty()) {
                if(candidates.size()==1 && from!=null && to!=null && matches(candidates.get(0),r,from,to))
                    entries.add(new Entry("ROUTE",r.key(),"REUSE",candidates.get(0).getId(),"Identity/geometry matched; statuses preserved"));
                else entries.add(new Entry("ROUTE",r.key(),"CONFLICT",null,"Existing name/endpoints with incompatible identity/stops; preserved"));
                continue;
            }
            if(r.legacy()) { entries.add(new Entry("ROUTE",r.key(),"CONFLICT",null,"Legacy route missing; prepare separately")); continue; }
            if((from!=null && from.getStatus()!=ActiveStatus.ACTIVE) || (to!=null && to.getStatus()!=ActiveStatus.ACTIVE)) {
                entries.add(new Entry("ROUTE",r.key(),"CONFLICT",null,"Inactive endpoint preserved; no new route created")); continue;
            }
            if(!apply) { entries.add(new Entry("ROUTE",r.key(),"CREATE",null,"Two direct stops; no association/fare/trip")); continue; }
            Route route=new Route(); route.setName(r.name()); route.setOriginLocation(from); route.setDestinationLocation(to);
            route.setEstimatedDistanceKm(BigDecimal.valueOf(r.km())); route.setEstimatedDurationMinutes(r.minutes());
            route.setStatus(RouteStatus.ACTIVE); route=routes.saveAndFlush(route);
            stops.saveAllAndFlush(List.of(stop(route,from,1,0,true,false),stop(route,to,2,r.minutes(),false,true)));
            definitions.requireUsable(route.getId()); // Same production geometry guard; failure rolls back this entire batch.
            entries.add(new Entry("ROUTE",r.key(),"CREATED",route.getId(),"Management catalogue only; no association/fare/trip"));
        }
        Location hcm=resolved.get("HCM"),hue=resolved.get("HUE");
        var coastal=routes.findCatalogueCandidates("TP. Hồ Chí Minh → Nha Trang → Đà Nẵng → Huế",
                hcm==null?-1L:hcm.getId(),hue==null?-1L:hue.getId());
        boolean coastalMatches=coastal.size()==1 && hcm!=null && hue!=null;
        if(coastalMatches) {
            var r=coastal.get(0);
            var rows=stops.findByRouteIdOrderByStopOrderAsc(r.getId());
            var keys=List.of("HCM","NHATRANG","DANANG","HUE");
            var offsets=List.of(0,540,1080,1260);
            coastalMatches=r.getName().equals("TP. Hồ Chí Minh → Nha Trang → Đà Nẵng → Huế")
                    && r.getOriginLocation().getId().equals(hcm.getId()) && r.getDestinationLocation().getId().equals(hue.getId())
                    && r.getEstimatedDurationMinutes()==1260 && r.getEstimatedDistanceKm().compareTo(BigDecimal.valueOf(1040))==0
                    && rows.size()==4;
            if(coastalMatches) for(int i=0;i<4;i++) {
                var location=resolved.get(keys.get(i));
                if(location==null || !matches(rows.get(i),location,i+1,offsets.get(i),i<3,i>0)) coastalMatches=false;
            }
        }
        entries.add(new Entry("ROUTE","COASTAL",coastalMatches?"REUSE":"CONFLICT",
                coastalMatches?coastal.get(0).getId():null,"Legacy coastal is never created, repaired or modified by M31"));
        return new Report(M31Catalogue.VERSION,batch,!apply,schema,List.copyOf(entries));
    }
    private boolean matches(Location l,M31Catalogue.Place p) {
        return l.getName().equals(p.name()) && Objects.equals(l.getProvince(),p.province())
                && Objects.equals(l.getDistrict(),p.district()) && Objects.equals(l.getAddress(),p.address())
                && l.getLatitude()==null && l.getLongitude()==null;
    }
    private boolean matches(Route r,M31Catalogue.Direction spec,Location from,Location to) {
        if(!r.getName().equals(spec.name()) || !r.getOriginLocation().getId().equals(from.getId())
                || !r.getDestinationLocation().getId().equals(to.getId())
                || r.getEstimatedDistanceKm().compareTo(BigDecimal.valueOf(spec.km()))!=0
                || r.getEstimatedDurationMinutes()!=spec.minutes()) return false;
        var rows=stops.findByRouteIdOrderByStopOrderAsc(r.getId());
        return rows.size()==2 && matches(rows.get(0),from,1,0,true,false)
                && matches(rows.get(1),to,2,spec.minutes(),false,true);
    }
    private boolean matches(RouteStop s,Location l,int order,int offset,boolean pickup,boolean dropoff) {
        return s.getLocation().getId().equals(l.getId()) && s.getStopOrder()==order
                && s.getEstimatedOffsetMinutes()==offset && s.isAllowPickup()==pickup && s.isAllowDropoff()==dropoff;
    }
    private RouteStop stop(Route r,Location l,int order,int offset,boolean pickup,boolean dropoff) {
        var s=new RouteStop(); s.setRoute(r); s.setLocation(l); s.setStopOrder(order);
        s.setEstimatedOffsetMinutes(offset); s.setAllowPickup(pickup); s.setAllowDropoff(dropoff);
        s.setStatus(ActiveStatus.ACTIVE); return s;
    }
}
