package com.busgo.demo.m31;

import java.util.*;

/** Fictional catalogue v1. Distances/durations are simulation parameters, not travel advice. */
public final class M31Catalogue {
    public static final String VERSION = "m31.2-v1";
    public enum Batch { PILOT, FULL }
    public record Place(String key, String name, String province, String district, String address,
                        boolean legacy) {}
    public record Direction(String key, String name, String from, String to, int km, int minutes,
                            boolean legacy) {}
    private M31Catalogue() {}

    public static List<Place> places(Batch batch) {
        var rows = new ArrayList<>(List.of(
            new Place("HCM", "Bến xe TP. Hồ Chí Minh", "TP. Hồ Chí Minh", "Thành phố Thủ Đức", "Khu vực cửa ngõ phía Đông TP. Hồ Chí Minh", true),
            new Place("DALAT", "Bến xe Đà Lạt", "Lâm Đồng", "Đà Lạt", "Khu vực trung tâm Đà Lạt", true),
            new Place("BMT", "Bến xe Buôn Ma Thuột", "Đắk Lắk", "Buôn Ma Thuột", "Khu vực trung tâm Buôn Ma Thuột", true),
            new Place("NHATRANG", "Bến xe Nha Trang", "Khánh Hòa", "Nha Trang", "Khu vực trung tâm Nha Trang", true),
            new Place("DANANG", "Bến xe Đà Nẵng", "Đà Nẵng", "Cẩm Lệ", "Khu vực cửa ngõ Đà Nẵng", true),
            new Place("HUE", "Bến xe Huế", "Thừa Thiên Huế", "Huế", "Khu vực trung tâm Huế", true),
            demo("CANTHO", "Cần Thơ"), demo("VUNGTAU", "Vũng Tàu")));
        if (batch == Batch.FULL) rows.addAll(List.of(demo("PHANTHIET", "Phan Thiết"),
                demo("QUYNHON", "Quy Nhơn"), demo("QUANGNGAI", "Quảng Ngãi"), demo("PLEIKU", "Pleiku")));
        return List.copyOf(rows);
    }
    private static Place demo(String key, String city) {
        // No unverified administrative units, station addresses or coordinates.
        return new Place(key, "Điểm đón demo " + city, null, null, null, false);
    }
    public static List<Direction> routes(Batch batch) {
        var rows = new ArrayList<Direction>();
        pair(rows,"HCM","TP. Hồ Chí Minh","DALAT","Đà Lạt",310,420,true);
        pair(rows,"HCM","TP. Hồ Chí Minh","BMT","Buôn Ma Thuột",350,480,true);
        pair(rows,"HCM","TP. Hồ Chí Minh","NHATRANG","Nha Trang",430,540,true);
        pair(rows,"DANANG","Đà Nẵng","HUE","Huế",100,180,true);
        pair(rows,"HCM","TP. Hồ Chí Minh","CANTHO","Cần Thơ",170,240,false);
        pair(rows,"HCM","TP. Hồ Chí Minh","VUNGTAU","Vũng Tàu",100,150,false);
        if (batch == Batch.FULL) {
            pair(rows,"HCM","TP. Hồ Chí Minh","PHANTHIET","Phan Thiết",200,270,false);
            pair(rows,"NHATRANG","Nha Trang","QUYNHON","Quy Nhơn",220,300,false);
            pair(rows,"QUYNHON","Quy Nhơn","DANANG","Đà Nẵng",320,420,false);
            pair(rows,"DANANG","Đà Nẵng","QUANGNGAI","Quảng Ngãi",130,180,false);
            pair(rows,"BMT","Buôn Ma Thuột","PLEIKU","Pleiku",180,240,false);
            pair(rows,"DALAT","Đà Lạt","NHATRANG","Nha Trang",140,210,false);
        }
        return List.copyOf(rows);
    }
    private static void pair(List<Direction> rows, String a, String an, String b, String bn,
                             int km, int minutes, boolean legacy) {
        rows.add(new Direction(a+"-"+b,(legacy?"":"Demo · ")+an+" → "+bn,a,b,km,minutes,legacy));
        rows.add(new Direction(b+"-"+a,(legacy?"":"Demo · ")+bn+" → "+an,b,a,km,minutes,legacy));
    }
    public static void validate(Batch batch) {
        var places = places(batch);
        var keys = new HashSet<String>();
        for (var p : places) if (!keys.add(p.key()) || p.name().isBlank())
            throw new IllegalStateException("Invalid location manifest");
        var pairs = new HashSet<String>();
        for (var r : routes(batch)) if (!keys.contains(r.from()) || !keys.contains(r.to())
                || r.from().equals(r.to()) || r.km()<=0 || r.minutes()<=0 || !pairs.add(r.key()))
            throw new IllegalStateException("Invalid route manifest");
    }
}
