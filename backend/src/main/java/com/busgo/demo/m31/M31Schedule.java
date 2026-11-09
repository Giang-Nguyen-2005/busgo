package com.busgo.demo.m31;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Simulated VND prices per operator/journey, never per bus type. */
public final class M31Schedule {
    public static final String VERSION="m31.4-v1";
    public record Price(BigDecimal amount,boolean legacy) {}
    public record Item(String kind,String key,String action,Long id,String reason,BigDecimal price) {}
    public record Departure(String key,String action,Long tripId,String reason,M31Fleet.Leg leg,
                            String busType,int seats,int inventory,BigDecimal grossFare) {}
    public record Batch(String plate,String initialLocation,boolean placementAcknowledged,
                        List<Item> catalogue,List<Departure> departures,boolean rolledBack) {}
    public record Report(String manifest,LocalDate anchor,int horizon,boolean dryRun,List<Batch> batches,
                         long created,long reused,long planned,long skipped) {}
    private M31Schedule() {}
    public static String key(M31Fleet.Leg leg) { return VERSION+":"+leg.plate()+":"+leg.route()+":"+leg.departure(); }
    public static Price price(String owner,String route) {
        String pair=route;
        var direct=M31Catalogue.routes(M31Catalogue.Batch.FULL).stream().filter(r->r.key().equals(route)).findFirst().orElseThrow();
        for(var rotation:M31Fleet.rotations()) {
            if(!rotation.owner().equals(owner)) continue;
            var r=M31Catalogue.routes(M31Catalogue.Batch.FULL).stream().filter(s->s.key().equals(rotation.forwardRoute())).findFirst().orElseThrow();
            if((r.from().equals(direct.from())&&r.to().equals(direct.to())) || (r.from().equals(direct.to())&&r.to().equals(direct.from()))) { pair=r.key(); break; }
        }
        String identity=owner+":"+pair;
        int value=switch(identity) {
            case "DEMO-ANPHU:HCM-DALAT" -> 250000;
            case "DEMO-ANPHU:HCM-NHATRANG" -> 290000;
            case "DEMO-ANPHU:HCM-CANTHO" -> 160000;
            case "DEMO-ANPHU:HCM-PHANTHIET" -> 180000;
            case "DEMO-MINHTHANH:HCM-DALAT" -> 300000;
            case "DEMO-MINHTHANH:HCM-VUNGTAU" -> 150000;
            case "DEMO-MINHTHANH:DALAT-NHATRANG" -> 200000;
            case "DEMO-MINHTHANH:DANANG-HUE" -> 140000;
            case "DEMO-TAYNGUYEN:HCM-BMT" -> 260000;
            case "DEMO-TAYNGUYEN:BMT-PLEIKU" -> 160000;
            case "DEMO-TAYNGUYEN:NHATRANG-QUYNHON" -> 210000;
            case "DEMO-TAYNGUYEN:QUYNHON-DANANG" -> 280000;
            case "DEMO-TAYNGUYEN:DANANG-QUANGNGAI" -> 120000;
            default -> throw new IllegalArgumentException("Journey is outside the M31 rotation matrix");
        };
        boolean legacy=Set.of("DEMO-ANPHU:HCM-DALAT","DEMO-ANPHU:HCM-NHATRANG","DEMO-MINHTHANH:HCM-DALAT",
                "DEMO-MINHTHANH:DANANG-HUE","DEMO-TAYNGUYEN:HCM-BMT").contains(identity);
        return new Price(BigDecimal.valueOf(value),legacy);
    }
    public static List<M31Fleet.Leg> legs(LocalDate anchor,int horizon,String plate) {
        Objects.requireNonNull(anchor,"Explicit stable anchor is required");
        return M31Fleet.plan(anchor,horizon).stream().filter(l->l.plate().equals(plate)).toList();
    }
}
