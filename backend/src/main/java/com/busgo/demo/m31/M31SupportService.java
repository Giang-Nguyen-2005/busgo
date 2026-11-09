package com.busgo.demo.m31;

import com.busgo.common.security.CurrentUser;
import com.busgo.media.OperatorMediaService;
import com.busgo.operations.*;
import com.busgo.promotion.*;
import com.busgo.user.entity.RoleCode;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Explicit isolated supporting-content command. Existing rows always win, including edited demo rows. */
@Service @Profile("dev & !demo & !prod & !production")
public class M31SupportService {
    public record Entry(String owner,String kind,String key,String action,String reason) {}
    public record Report(String manifest,boolean dryRun,List<Entry> entries) {}
    private final M31CatalogueService catalogue;
    private final JdbcTemplate db;
    private final OperatorMediaService media;
    private final VoucherService vouchers;
    private final OperationsService operations;
    public M31SupportService(M31CatalogueService catalogue,JdbcTemplate db,OperatorMediaService media,
            VoucherService vouchers,OperationsService operations) {
        this.catalogue=catalogue;this.db=db;this.media=media;this.vouchers=vouchers;this.operations=operations;
    }
    @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED)
    public Report dryRun(Path assets) throws IOException { return run(false,assets); }
    @Transactional(isolation=Isolation.READ_COMMITTED,rollbackFor=Exception.class)
    public Report apply(Path assets) throws IOException { return run(true,assets); }
    private Report run(boolean apply,Path assets) throws IOException {
        var baseline=catalogue.dryRun(M31Catalogue.Batch.FULL);
        if(baseline.entries().stream().anyMatch(e->!e.action().equals("REUSE")))
            throw new IllegalStateException("Catalogue differs; inspect M31.2 dry-run first");
        if(db.queryForObject("SELECT COUNT(*) FROM buses",Long.class)!=19L || db.queryForObject("SELECT COUNT(*) FROM trips",Long.class)!=385L)
            throw new IllegalStateException("Expected M31.4 fleet/window; no repair performed");
        // Serialize cooperative importers. Domain services retain their own owner, image and crew guards.
        if(apply) db.queryForObject("SELECT id FROM roles WHERE code='CUSTOMER' FOR UPDATE",Long.class);
        var result=new ArrayList<Entry>();
        String[] shortNames={"AP","MT","TN"};
        String[] logos={"operator-an-phu-express-logo.webp","operator-minh-thanh-limousine-logo.webp","operator-tay-nguyen-travel-logo.webp"};
        String[] representative={"HCM-CANTHO","HCM-VUNGTAU","DANANG-QUANGNGAI"};
        int index=0;
        for(var spec:M31Fleet.operators()) {
            int i=index++;
            var owners=db.queryForList("SELECT id FROM transport_operators WHERE code=? AND name=? AND status='ACTIVE'",Long.class,spec.code(),spec.name());
            if(owners.size()!=1) throw new IllegalStateException("Missing/changed demo operator "+spec.code());
            long owner=owners.get(0);
            var admins=db.queryForList("""
                SELECT u.id FROM users u JOIN operator_staff s ON s.user_id=u.id
                WHERE s.operator_id=? AND s.status='ACTIVE' AND u.status='ACTIVE' AND u.deleted_at IS NULL
                AND (SELECT COUNT(*) FROM operator_staff WHERE user_id=u.id)=1
                AND (SELECT COUNT(*) FROM user_roles WHERE user_id=u.id)=1
                AND EXISTS(SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.code='OPERATOR_ADMIN')
                ORDER BY u.id
                """,Long.class,owner);
            if(admins.isEmpty()) throw new IllegalStateException("Dedicated active admin required");
            var actor=new CurrentUser(admins.get(0),List.of(RoleCode.OPERATOR_ADMIN));
            var previous=SecurityContextHolder.getContext();
            var temporary=SecurityContextHolder.createEmptyContext();
            temporary.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor,null,actor.authorities()));
            try {
                SecurityContextHolder.setContext(temporary);
                // Lock only during writes; never replace an existing logo, even if its URL points elsewhere.
                if(apply) db.queryForObject("SELECT id FROM transport_operators WHERE id=? FOR UPDATE",Long.class,owner);
                boolean logoEmpty=db.queryForObject("SELECT COUNT(*) FROM operator_media WHERE operator_id=? AND category='LOGO'",Long.class,owner)==0
                    && db.queryForObject("SELECT COALESCE(logo_url,'') FROM transport_operators WHERE id=?",String.class,owner).isBlank();
                var logo=file(assets,logos[i]);
                if(apply && logoEmpty) media.logo(actor,logo);
                result.add(entry(spec.code(),"LOGO",logos[i],logoEmpty,apply));
                for(var type:M31Fleet.types().entrySet()) {
                    long typeId=db.queryForObject("SELECT id FROM bus_types WHERE name=?",Long.class,type.getValue());
                    if(db.queryForObject("SELECT COUNT(*) FROM buses WHERE operator_id=? AND bus_type_id=? AND deleted_at IS NULL",Long.class,owner,typeId)==0) continue;
                    boolean empty=media.own(actor,typeId).isEmpty();
                    // No cabin or uncertain seat-layout photograph is attached to LIMO22.
                    String image=switch(type.getKey()) { case "SLEEPER34"->"interior-sleeper-standard.webp";case "LIMO22"->"bus-limousine.jpg";default->"bus-standard.jpg"; };
                    var asset=file(assets,image);
                    if(apply && empty) media.upload(actor,typeId,type.getKey().equals("SLEEPER34")?OperatorMediaService.Category.INTERIOR:OperatorMediaService.Category.EXTERIOR,
                        "[DEMO M31] Ảnh minh họa "+type.getValue()+"; không xác nhận bố trí, tiện nghi hoặc xe thực tế.",List.of(asset));
                    result.add(entry(spec.code(),"GALLERY",type.getKey(),empty,apply));
                }
                for(var voucher:manifest(shortNames[i],owner)) {
                    boolean empty=db.queryForObject("SELECT COUNT(*) FROM operator_vouchers WHERE operator_id=? AND code=?",Long.class,owner,voucher.terms().code())==0;
                    if(apply && empty) {
                        long id=((Number)vouchers.create(actor,voucher.terms()).get("id")).longValue();
                        vouchers.state(actor,id,!voucher.paused());
                        if(voucher.visible()) vouchers.visibility(actor,id,true);
                    }
                    result.add(entry(spec.code(),"VOUCHER",voucher.terms().code(),empty,apply));
                }
                var crew=new ArrayList<OperationsDtos.CrewInput>();
                for(var duty:OperationsDtos.Capability.values()) {
                    String code="M31-"+shortNames[i]+"-"+duty;
                    var employees=db.queryForList("SELECT id FROM operator_employees WHERE operator_id=? AND employee_code=?",Long.class,owner,code);
                    boolean empty=employees.isEmpty();
                    Long id=empty?null:employees.get(0);
                    if(apply && empty) id=operations.saveEmployee(actor,null,new OperationsDtos.EmployeeInput(code,
                        "Nhân sự demo "+shortNames[i]+" "+duty,"DEMO-NOT-DIALABLE",OperationsDtos.EmployeeStatus.ACTIVE,Set.of(duty),
                        duty==OperationsDtos.Capability.DRIVER?"DEMO-NOT-VALID-"+shortNames[i]:null,
                        duty==OperationsDtos.Capability.DRIVER?"DEMO":null,duty==OperationsDtos.Capability.DRIVER?LocalDate.of(2030,1,1):null,null)).id();
                    if(id!=null) crew.add(new OperationsDtos.CrewInput(id,duty));
                    result.add(entry(spec.code(),"EMPLOYEE",code,empty,apply));
                }
                long route=route(representative[i]);
                var trips=db.queryForList("""
                    SELECT t.id FROM trips t JOIN operator_routes r ON r.id=t.operator_route_id
                    WHERE r.operator_id=? AND r.route_id=? AND t.departure_time=?
                    """,Long.class,owner,route,com.busgo.common.time.JpaJdbcTime.parameter(LocalDateTime.of(2026,11,13,23,0)));
                if(trips.size()!=1) throw new IllegalStateException("Representative trip missing/ambiguous: "+representative[i]);
                long trip=trips.get(0);
                if(apply) db.queryForObject("SELECT id FROM trips WHERE id=? FOR UPDATE",Long.class,trip);
                boolean noHistory=db.queryForObject("SELECT COUNT(*) FROM trip_crew_assignments WHERE trip_id=?",Long.class,trip)==0;
                if(apply && noHistory) operations.replaceCrew(actor,trip,new OperationsDtos.CrewReplacement(crew));
                result.add(entry(spec.code(),"CREW",representative[i]+"@2026-11-14T06:00",noHistory,apply));
            } finally { SecurityContextHolder.setContext(previous); }
        }
        return new Report("m31.5-v1",!apply,List.copyOf(result));
    }
    private Entry entry(String owner,String kind,String key,boolean empty,boolean apply) {
        return new Entry(owner,kind,key,empty?(apply?"CREATED":"CREATE"):"PRESERVE",empty?"Domain validation; isolated demo only":"Existing content/history preserved without editing, reordering or re-featuring; inspect differences manually");
    }
    private long route(String key) {
        var spec=M31Catalogue.routes(M31Catalogue.Batch.FULL).stream().filter(r->r.key().equals(key)).findFirst().orElseThrow();
        return db.queryForObject("SELECT id FROM routes WHERE name=?",Long.class,spec.name());
    }
    private record Campaign(VoucherDtos.Terms terms,boolean visible,boolean paused) {}
    private List<Campaign> manifest(String owner,long operator) {
        return switch(owner) {
            case "AP"->List.of(campaign("M31-AP-FIX20","FIXED_AMOUNT",20000,null,100000,40,1,operator,"HCM-PHANTHIET",true,false,false),
                campaign("M31-AP-MIN50","FIXED_AMOUNT",50000,null,1000000,10,1,operator,"HCM-NHATRANG",true,false,false));
            case "MT"->List.of(campaign("M31-MT-PCT10","PERCENT",10,50000,200000,60,2,operator,null,true,false,false),
                campaign("M31-MT-FUTURE10","FIXED_AMOUNT",10000,null,100000,10,1,operator,null,true,false,true));
            default->List.of(campaign("M31-TN-PRIVATE15","FIXED_AMOUNT",15000,null,100000,30,1,operator,null,false,false,false),
                campaign("M31-TN-PAUSED10","FIXED_AMOUNT",10000,null,100000,5,1,operator,null,false,true,false));
        };
    }
    private Campaign campaign(String code,String type,int value,Integer cap,int minimum,int quota,int perCustomer,long owner,String route,boolean visible,boolean paused,boolean future) {
        Long association=route==null?null:db.queryForObject("SELECT id FROM operator_routes WHERE operator_id=? AND route_id=?",Long.class,owner,route(route));
        return new Campaign(new VoucherDtos.Terms(code,"DEMO · "+code,"Chỉ mô phỏng trong DB test cô lập; không phải khuyến mãi thực tế. Điều kiện và hạn mức hiển thị kèm mã.",type,
            BigDecimal.valueOf(value),cap==null?null:BigDecimal.valueOf(cap),BigDecimal.valueOf(minimum),
            OffsetDateTime.parse(future?"2026-11-20T00:00:00+07:00":"2026-11-08T00:00:00+07:00"),
            OffsetDateTime.parse("2026-11-28T00:00:00+07:00"),quota,perCustomer,association),visible,paused);
    }
    private MultipartFile file(Path directory,String name) throws IOException {
        Path root=directory.toRealPath(),path=root.resolve(name).toRealPath();
        if(!path.startsWith(root)) throw new IllegalArgumentException("Asset outside supplied directory");
        byte[] content=Files.readAllBytes(path);
        return new MultipartFile() {
            public String getName(){return "file";} public String getOriginalFilename(){return name;}
            public String getContentType(){return name.endsWith("webp")?"image/webp":"image/jpeg";}
            public boolean isEmpty(){return content.length==0;} public long getSize(){return content.length;}
            public byte[] getBytes(){return content.clone();} public InputStream getInputStream(){return new ByteArrayInputStream(content);}
            public void transferTo(File target) throws IOException {Files.write(target.toPath(),content);}
        };
    }
}
