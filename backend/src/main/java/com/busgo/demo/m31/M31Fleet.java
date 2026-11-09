package com.busgo.demo.m31;

import com.busgo.user.entity.RoleCode;
import java.time.*;
import java.util.*;

/** Demo identities and vehicle circulation proposals; no trips or associations are persisted here. */
public final class M31Fleet {
    public static final String VERSION="m31.3-v1";
    public static final String AP="DEMO-ANPHU", MT="DEMO-MINHTHANH", TN="DEMO-TAYNGUYEN";
    public record Operator(String code,String name,String description) {}
    public record Account(String owner,String email,String staffCode,RoleCode role,boolean legacy,
                          String name,String phone,String secretProperty) {}
    public record Vehicle(String owner,String plate,String type,boolean legacy,String purpose) {}
    public record Rotation(String owner,String plate,String forwardRoute,LocalTime departure,int turnaroundMinutes) {}
    public record Leg(String plate,String owner,String route,String from,String to,
                      LocalDateTime departure,LocalDateTime arrival) {}
    private M31Fleet() {}
    public static List<Operator> operators() {
        return List.of(
            new Operator(AP,"An Phú Express","An Phú Express là nhà xe trong bộ dữ liệu demo BusGo, giới thiệu các hành trình mẫu từ TP. Hồ Chí Minh đến Đà Lạt, Nha Trang và miền Nam. Đội xe mẫu gồm giường nằm 34 chỗ và ghế ngồi 40 chỗ. Lịch, giá và chỗ trống cần được kiểm tra theo ngày; các tuyến dự kiến chưa có lịch không phải dịch vụ đang mở bán. Đặt vé và thanh toán đều là mô phỏng, không tiếp nhận yêu cầu vận chuyển thật; nội dung không chứng nhận pháp lý hay liên hệ vận hành."),
            new Operator(MT,"Minh Thành Limousine","Minh Thành Limousine thuộc bộ dữ liệu demo BusGo, với xe mẫu limousine 22 chỗ, giường nằm 34 chỗ và ghế ngồi 40 chỗ. Nội dung giới thiệu các hành trình dự kiến giữa TP. Hồ Chí Minh, Đà Lạt, Vũng Tàu và duyên hải miền Trung. Chỉ những chuyến có lịch và giá hợp lệ mới có thể đặt trong bản demo. Đây là dữ liệu mô phỏng, không xác nhận tiện nghi VIP, tư cách pháp lý hay dịch vụ vận chuyển thật; thanh toán không phát sinh tiền thật."),
            new Operator(TN,"Tây Nguyên Travel","Tây Nguyên Travel là nhà xe mẫu phục vụ trải nghiệm thử BusGo. Đội xe mô phỏng gồm ghế ngồi 40 chỗ, limousine 22 chỗ và giường nằm 34 chỗ, chuẩn bị cho hành trình cao nguyên và duyên hải như Buôn Ma Thuột, Pleiku, Nha Trang, Quy Nhơn. Tuyến dự kiến không đồng nghĩa có chuyến mỗi ngày; vui lòng kiểm tra dữ liệu khi chọn hành trình. Nội dung và liên hệ chỉ dành cho demo, không chứng thực doanh nghiệp hoặc tiếp nhận vận chuyển thật. Đặt vé và thanh toán đều được mô phỏng."));
    }
    public static List<Account> accounts() {
        return List.of(
            new Account(AP,"operator.admin@anphu-demo.example","DEMO-ANPHU-ADMIN",RoleCode.OPERATOR_ADMIN,true,null,null,null),
            new Account(AP,"operator.staff@anphu-demo.example","DEMO-ANPHU-STAFF",RoleCode.OPERATOR_STAFF,true,null,null,null),
            new Account(MT,"operator.admin@minhthanh-demo.example","DEMO-MINHTHANH-ADMIN",RoleCode.OPERATOR_ADMIN,true,null,null,null),
            fresh(MT,"operator.staff@minhthanh-demo.example","M31-MT-STAFF",RoleCode.OPERATOR_STAFF,"Nhân viên Minh Thành (demo)","minhthanh-staff"),
            fresh(TN,"operator.admin@taynguyen-demo.example","M31-TN-ADMIN",RoleCode.OPERATOR_ADMIN,"Quản trị Tây Nguyên (demo)","taynguyen-admin"),
            fresh(TN,"operator.staff@taynguyen-demo.example","M31-TN-STAFF",RoleCode.OPERATOR_STAFF,"Nhân viên Tây Nguyên (demo)","taynguyen-staff"));
    }
    private static Account fresh(String o,String email,String code,RoleCode role,String name,String key) {
        return new Account(o,email,code,role,false,name,"DEMO-NOT-DIALABLE","busgo.m31.fleet."+key+"-password");
    }
    public static Map<String,String> types() {
        return Map.of("LIMO22","Limousine 22 chỗ","SLEEPER34","Giường nằm 34 chỗ","STANDARD40","Ghế ngồi 40 chỗ");
    }
    public static List<Vehicle> vehicles() {
        return List.of(
            v(AP,"51B-770.01","SLEEPER34",true,"HCM-DALAT"),v(AP,"51B-770.02","STANDARD40",true,"HCM-CANTHO"),
            v(AP,"51B-770.03","SLEEPER34",true,"LEGACY-COASTAL-DEFERRED"),
            v(AP,"DEMO-M31-AP-01","SLEEPER34",false,"HCM-NHATRANG"),v(AP,"DEMO-M31-AP-02","STANDARD40",false,"HCM-PHANTHIET"),
            v(AP,"DEMO-M31-AP-03","SLEEPER34",false,"SPARE"),v(AP,"DEMO-M31-AP-04","STANDARD40",false,"SPARE"),
            v(MT,"50F-880.01","LIMO22",true,"HCM-DALAT"),v(MT,"50F-880.02","SLEEPER34",true,"DALAT-NHATRANG"),
            v(MT,"DEMO-M31-MT-01","LIMO22",false,"HCM-VUNGTAU"),v(MT,"DEMO-M31-MT-02","STANDARD40",false,"DANANG-HUE"),
            v(MT,"DEMO-M31-MT-03","LIMO22",false,"SPARE"),v(MT,"DEMO-M31-MT-04","SLEEPER34",false,"SPARE"),
            v(TN,"47B-660.01","STANDARD40",true,"HCM-BMT"),v(TN,"47B-660.02","LIMO22",true,"BMT-PLEIKU"),
            v(TN,"DEMO-M31-TN-01","SLEEPER34",false,"NHATRANG-QUYNHON"),v(TN,"DEMO-M31-TN-02","SLEEPER34",false,"QUYNHON-DANANG"),
            v(TN,"DEMO-M31-TN-03","STANDARD40",false,"DANANG-QUANGNGAI"),v(TN,"DEMO-M31-TN-04","SLEEPER34",false,"SPARE"));
    }
    private static Vehicle v(String o,String p,String t,boolean legacy,String purpose) { return new Vehicle(o,p,t,legacy,purpose); }
    public static List<Rotation> rotations() {
        return vehicles().stream().filter(v->!v.purpose().equals("SPARE") && !v.purpose().startsWith("LEGACY-"))
            .map(v->new Rotation(v.owner(),v.plate(),v.purpose(),
                LocalTime.of(v.plate().equals("50F-880.01")?8:v.plate().equals("DEMO-M31-TN-02")?18:6,
                        v.plate().equals("51B-770.01")?30:0),90)).toList();
    }
    /** Pure proposal in Vietnam local time. Initial base is the first leg's origin; never inferred from DB. */
    public static List<Leg> plan(LocalDate anchor,int days) {
        if(days<1 || days>14) throw new IllegalArgumentException("Demo horizon must be 1–14 days");
        var specs=new HashMap<String,M31Catalogue.Direction>();
        for(var r:M31Catalogue.routes(M31Catalogue.Batch.FULL)) specs.put(r.key(),r);
        var legs=new ArrayList<Leg>();
        for(var rotation:rotations()) {
            var r=specs.get(rotation.forwardRoute());
            var back=specs.get(r.to()+"-"+r.from());
            LocalTime returnTime=rotation.departure().plusMinutes(r.minutes()+rotation.turnaroundMinutes());
            for(int d=0;d<days;d++) {
                var date=anchor.plusDays(d);
                var dep=date.atTime(rotation.departure()); var ret=date.atTime(returnTime);
                legs.add(new Leg(rotation.plate(),rotation.owner(),r.key(),r.from(),r.to(),dep,dep.plusMinutes(r.minutes())));
                legs.add(new Leg(rotation.plate(),rotation.owner(),back.key(),back.from(),back.to(),ret,ret.plusMinutes(back.minutes())));
            }
        }
        legs.sort(Comparator.comparing(Leg::plate).thenComparing(Leg::departure));
        return List.copyOf(legs);
    }
    public static void validate() {
        var vehicleMap=new HashMap<String,Vehicle>();
        for(var v:vehicles()) if(vehicleMap.put(v.plate(),v)!=null || !types().containsKey(v.type()))
            throw new IllegalStateException("Invalid fleet identity");
        var previous=new HashMap<String,Leg>();
        for(var leg:plan(LocalDate.of(2030,1,1),14)) {
            var before=previous.put(leg.plate(),leg);
            if(before!=null && (!before.to().equals(leg.from()) || before.arrival().plusMinutes(90).isAfter(leg.departure())))
                throw new IllegalStateException("Invalid circulation/buffer for "+leg.plate());
            if(!vehicleMap.get(leg.plate()).owner().equals(leg.owner())) throw new IllegalStateException("Wrong plan owner");
        }
    }
}
