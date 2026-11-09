package com.busgo.demo.m31;

import com.busgo.auth.PasswordPolicy;
import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.security.CurrentUser;
import com.busgo.fleet.*;
import com.busgo.fleet.entity.*;
import com.busgo.fleet.repository.*;
import com.busgo.operator.entity.*;
import com.busgo.operator.repository.*;
import com.busgo.user.entity.*;
import com.busgo.user.repository.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Isolated demo importer; profile filling is a separate explicit blank-only operation. */
@Service
@Profile("dev & !prod & !production")
public class M31FleetService {
    public record Entry(String owner,String kind,String key,String action,Long id,String reason) {}
    public record Report(String manifest,boolean dryRun,boolean fillBlankProfiles,List<Entry> entries) {
        public long count(String kind,String action) { return entries.stream().filter(e->e.kind().equals(kind)&&e.action().equals(action)).count(); }
    }
    private final M31CatalogueService catalogue;
    private final TransportOperatorRepository operators;
    private final BusRepository buses;
    private final BusTypeRepository types;
    private final BusTypeService layouts;
    private final BusService busService;
    private final UserRepository users;
    private final UserRoleRepository userRoles;
    private final RoleRepository roles;
    private final OperatorStaffRepository memberships;
    private final PasswordEncoder passwords;
    private final Environment env;
    private final JdbcTemplate jdbc;
    private final jakarta.persistence.EntityManager em;
    public M31FleetService(M31CatalogueService catalogue,TransportOperatorRepository operators,
            BusRepository buses,BusTypeRepository types,BusTypeService layouts,BusService busService,
            UserRepository users,UserRoleRepository userRoles,RoleRepository roles,OperatorStaffRepository memberships,
            PasswordEncoder passwords,Environment env,JdbcTemplate jdbc,jakarta.persistence.EntityManager em) {
        this.catalogue=catalogue; this.operators=operators; this.buses=buses; this.types=types; this.layouts=layouts;
        this.busService=busService; this.users=users; this.userRoles=userRoles; this.roles=roles;
        this.memberships=memberships; this.passwords=passwords; this.env=env; this.jdbc=jdbc; this.em=em;
    }
    @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED)
    public Report dryRun(boolean fillBlankProfiles) { return run(false,fillBlankProfiles); }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Report apply(boolean fillBlankProfiles) {
        baseline();
        jdbc.queryForObject("SELECT id FROM roles WHERE code='CUSTOMER' FOR UPDATE",Long.class);
        // Same cooperative lock as M31.2; do not run both commands concurrently.
        operators.findAll().stream().filter(o->M31Fleet.operators().stream().anyMatch(s->s.code().equals(o.getCode())))
            .sorted(Comparator.comparing(TransportOperator::getId)).forEach(o->em.refresh(operators.lockById(o.getId()).orElseThrow()));
        return run(true,fillBlankProfiles);
    }
    private void baseline() {
        var report=catalogue.dryRun(M31Catalogue.Batch.FULL); // Existing isolated-target / V28 guard, no seeder.
        if(report.entries().stream().anyMatch(e->!e.action().equals("REUSE")))
            throw new IllegalStateException("M31.2 catalogue baseline missing/conflicting; inspect its dry-run, never repair here");
        M31Fleet.validate();
    }
    private Map<String,BusType> resolveTypes() {
        var all=types.findAll(); var result=new HashMap<String,BusType>();
        if(all.size()!=3) throw new IllegalStateException("Expected exactly three existing bus types; no types will be changed");
        for(var entry:M31Fleet.types().entrySet()) {
            var matches=all.stream().filter(t->t.getName().equals(entry.getValue())).toList();
            if(matches.size()!=1) throw new IllegalStateException("Missing/ambiguous existing bus type "+entry.getKey());
            var type=layouts.requireUsable(matches.get(0).getId());
            int expected=switch(entry.getKey()) { case "LIMO22"->22; case "SLEEPER34"->34; default->40; };
            if(type.getSeatCount()!=expected) throw new IllegalStateException("Changed seat layout; preserve and inspect "+entry.getKey());
            result.put(entry.getKey(),type);
        }
        return result;
    }
    private Report run(boolean apply,boolean fill) {
        baseline();
        var busTypes=resolveTypes();
        var result=new ArrayList<Entry>();
        for(var spec:M31Fleet.operators()) {
            var op=operators.findByCode(spec.code()).orElse(null);
            if(op==null || !op.getName().equals(spec.name()) || op.getStatus()!=OperatorStatus.ACTIVE) {
                result.add(new Entry(spec.code(),"OPERATOR",spec.code(),"CONFLICT",op==null?null:op.getId(),"Missing/renamed/inactive operator preserved; no fourth operator")); continue;
            }
            var planned=new ArrayList<Entry>();
            for(var a:M31Fleet.accounts().stream().filter(a->a.owner().equals(spec.code())).toList()) planned.add(accountPlan(op,a));
            for(var v:M31Fleet.vehicles().stream().filter(v->v.owner().equals(spec.code())).toList()) {
                var bus=buses.findByLicensePlateIgnoreCase(v.plate()).orElse(null);
                if(bus==null) planned.add(new Entry(spec.code(),"BUS",v.plate(),v.legacy()?"CONFLICT":"CREATE",null,
                        v.legacy()?"Legacy bus missing; never recreated":"Demo identifier, no image; "+v.purpose()));
                else if(!bus.getOperator().getId().equals(op.getId()) || !bus.getBusType().getId().equals(busTypes.get(v.type()).getId()))
                    planned.add(new Entry(spec.code(),"BUS",v.plate(),"CONFLICT",bus.getId(),"Plate owner/type collision; preserved"));
                else planned.add(new Entry(spec.code(),"BUS",v.plate(),"REUSE",bus.getId(),
                        bus.getDeletedAt()!=null || bus.getStatus()!=BusStatus.AVAILABLE?"Unavailable/deleted bus preserved; not ready for scheduling":"Current bus/image preserved"));
            }
            boolean blocked=planned.stream().anyMatch(e->e.action().equals("CONFLICT")||e.action().equals("NEEDS_SECRET"));
            boolean willHaveAdmin=planned.stream().anyMatch(e->e.kind().equals("ACCOUNT") && e.key().contains("operator.admin@")
                    && (e.action().equals("CREATE") || (e.action().equals("REUSE") && activeAdmin(e.id(),op.getId()))));
            if(!willHaveAdmin) {
                blocked=true;
                planned.add(new Entry(spec.code(),"OPERATOR",spec.code(),"CONFLICT",op.getId(),"No dedicated active admin available/planned; preserve suspension"));
            }
            if(blocked) {
                for(var entry:planned) result.add(entry.action().equals("CREATE")
                        ?new Entry(entry.owner(),entry.kind(),entry.key(),"SKIP",null,"Operator preflight blocked; no partial operator import"):entry);
                result.add(new Entry(spec.code(),"PROFILE",spec.code(),"PRESERVE",op.getId(),"Operator preflight blocked"));
                continue;
            }
            for(var entry:planned) {
                if(!apply || !entry.action().equals("CREATE")) { result.add(entry); continue; }
                if(entry.kind().equals("ACCOUNT")) {
                    var a=M31Fleet.accounts().stream().filter(s->s.email().equals(entry.key())).findFirst().orElseThrow();
                    var user=createAccount(op,a);
                    result.add(new Entry(spec.code(),"ACCOUNT",entry.key(),"CREATED",user.getId(),"Dedicated role/membership; credential supplied externally"));
                } else {
                    var v=M31Fleet.vehicles().stream().filter(s->s.plate().equals(entry.key())).findFirst().orElseThrow();
                    var actor=admin(op.getId());
                    var old=SecurityContextHolder.getContext();
                    var temporary=SecurityContextHolder.createEmptyContext();
                    temporary.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor,null,
                            List.of(new SimpleGrantedAuthority("ROLE_OPERATOR_ADMIN"))));
                    try {
                        SecurityContextHolder.setContext(temporary);
                        var bus=busService.create(actor,new FleetDtos.CreateBusRequest(v.plate(),busTypes.get(v.type()).getId()));
                        result.add(new Entry(spec.code(),"BUS",entry.key(),"CREATED",bus.id(),"Created through BusService with existing usable layout; no image"));
                    } finally { SecurityContextHolder.setContext(old); }
                }
            }
            String current=op.getPublicDescription();
            if(current!=null && !current.isBlank())
                result.add(new Entry(spec.code(),"PROFILE",spec.code(),current.equals(spec.description())?"REUSE":"PRESERVE",op.getId(),"Nonblank description/logo/contact/legal fields preserved"));
            else if(!fill) result.add(new Entry(spec.code(),"PROFILE",spec.code(),"PROPOSE",op.getId(),spec.description()));
            else if(!apply) result.add(new Entry(spec.code(),"PROFILE",spec.code(),"FILL_BLANK",op.getId(),spec.description()));
            else {
                op.setPublicDescription(spec.description()); operators.saveAndFlush(op);
                result.add(new Entry(spec.code(),"PROFILE",spec.code(),"FILLED_BLANK",op.getId(),"Explicit blank-only description applied; identity/contact/logo/legal unchanged"));
            }
        }
        return new Report(M31Fleet.VERSION,!apply,fill,List.copyOf(result));
    }
    private Entry accountPlan(TransportOperator op,M31Fleet.Account a) {
        var u=users.findByEmail(a.email()).orElse(null);
        if(u!=null) {
            var links=memberships.findByUserId(u.getId());
            boolean matches=userRoles.findRoleCodesByUserId(u.getId()).equals(List.of(a.role())) && links.size()==1
                    && links.get(0).getOperator().getId().equals(op.getId()) && links.get(0).getStaffCode().equals(a.staffCode())
                    && (a.legacy() || (a.name().equals(u.getFullName()) && a.phone().equals(u.getPhone())));
            return new Entry(a.owner(),"ACCOUNT",a.email(),matches?"REUSE":"CONFLICT",u.getId(),
                    matches?"Password/contact/status unchanged; no secret required for reuse":"Reserved identity/role/membership collision; no account mutation");
        }
        if(a.legacy() || memberships.existsByOperatorIdAndStaffCodeIgnoreCase(op.getId(),a.staffCode()))
            return new Entry(a.owner(),"ACCOUNT",a.email(),"CONFLICT",null,"Missing legacy account or staff-code collision");
        String secret=env.getProperty(a.secretProperty());
        try { PasswordPolicy.validate(secret); }
        catch(RuntimeException e) { return new Entry(a.owner(),"ACCOUNT",a.email(),"NEEDS_SECRET",null,"Supply valid external credential property "+a.secretProperty()); }
        return new Entry(a.owner(),"ACCOUNT",a.email(),"CREATE",null,"One role/one owner; externally supplied credential");
    }
    private User createAccount(TransportOperator op,M31Fleet.Account a) {
        String secret=env.getProperty(a.secretProperty()); PasswordPolicy.validate(secret);
        User u=new User(); u.setEmail(a.email()); u.setFullName(a.name()); u.setPhone(a.phone());
        u.setPasswordHash(passwords.encode(secret)); u.setStatus(UserStatus.ACTIVE); users.saveAndFlush(u);
        userRoles.saveAndFlush(new UserRole(u,roles.findByCode(a.role()).orElseThrow()));
        var link=new OperatorStaff(); link.setOperator(op); link.setUser(u); link.setStaffCode(a.staffCode());
        link.setStatus(ActiveStatus.ACTIVE); memberships.saveAndFlush(link);
        return u;
    }
    private boolean activeAdmin(Long id,Long owner) {
        var user=users.findById(id).orElseThrow();
        return user.getStatus()==UserStatus.ACTIVE && user.getDeletedAt()==null
                && userRoles.findRoleCodesByUserId(id).equals(List.of(RoleCode.OPERATOR_ADMIN))
                && memberships.findByUserId(id).stream().anyMatch(s->s.getOperator().getId().equals(owner)&&s.getStatus()==ActiveStatus.ACTIVE);
    }
    private CurrentUser admin(Long owner) {
        return memberships.lockActiveLoginCapableAdmins(owner).stream().map(OperatorStaff::getUser)
            .filter(u->activeAdmin(u.getId(),owner)).map(u->new CurrentUser(u.getId(),List.of(RoleCode.OPERATOR_ADMIN)))
            .findFirst().orElseThrow(()->new IllegalStateException("Dedicated active admin required; no bypass"));
    }
}
