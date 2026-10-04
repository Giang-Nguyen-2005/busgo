package com.busgo.fleet;

import com.busgo.common.exception.*;
import com.busgo.common.time.*;
import com.busgo.fleet.entity.*;
import com.busgo.fleet.repository.BusRepository;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Caller owns transaction. Never acquire a trip lock after acquiring a bus lock. */
@Service
public class FleetMaintenanceGuard {
    private final JdbcTemplate db;
    private final BusRepository buses;
    private final EntityManager em;
    public FleetMaintenanceGuard(JdbcTemplate db, BusRepository buses, EntityManager em) {
        this.db=db; this.buses=buses; this.em=em;
    }
    public Bus lockFleetBus(long operator, long id) {
        // Hide ownership before reading any trip IDs. Trip assignments/times are immutable.
        buses.findOwnedById(id,operator).orElseThrow(FleetMaintenanceGuard::missingBus);
        var ids=db.queryForList("SELECT t.id FROM trips t JOIN operator_routes r ON r.id=t.operator_route_id WHERE t.bus_id=? AND r.operator_id=? AND t.status IN ('SCHEDULED','BOARDING','DEPARTED') ORDER BY t.id",Long.class,id,operator);
        for(long trip:ids) db.queryForList("SELECT id FROM trips WHERE id=? FOR UPDATE",Long.class,trip);
        Bus bus=buses.findOwnedByIdForUpdate(id,operator).orElseThrow(FleetMaintenanceGuard::missingBus);
        em.refresh(bus); // Defeat any entity loaded during the ownership probe.
        var current=db.queryForList("SELECT t.id FROM trips t JOIN operator_routes r ON r.id=t.operator_route_id WHERE t.bus_id=? AND r.operator_id=? AND t.status IN ('SCHEDULED','BOARDING','DEPARTED') ORDER BY t.id",Long.class,id,operator);
        if(!ids.containsAll(current)) throw conflict("FLEET_PLAN_CHANGED","Lịch chuyến vừa thay đổi. Tải lại và thử lại thao tác đội xe.",null);
        return bus;
    }
    public boolean hasActive(long bus) {
        return db.queryForObject("SELECT COUNT(*) FROM bus_maintenance_records WHERE bus_id=? AND status='IN_PROGRESS'",Long.class,bus)>0;
    }
    public String warning(long bus, LocalDateTime start, LocalDateTime end) {
        if(hasActive(bus)) return "Xe đang bảo trì. Hoàn tất bảo trì trước khi phân công hoặc đón khách.";
        long count=db.queryForObject("SELECT COUNT(*) FROM bus_maintenance_records WHERE bus_id=? AND status='SCHEDULED' AND scheduled_start<? AND scheduled_end>?",Long.class,bus,JpaJdbcTime.parameter(end),JpaJdbcTime.parameter(start));
        return count>0 ? "Lịch bảo trì trùng thời gian vận hành chuyến. Hủy lịch bảo trì hoặc chọn xe khác." : "";
    }
    public void requireAssignable(long bus, LocalDateTime start, LocalDateTime end) {
        String warning=warning(bus,start,end);
        if(!warning.isEmpty()) throw conflict("BUS_MAINTENANCE_CONFLICT",warning,null);
    }
    public void changeStatus(Bus bus, BusStatus status, String reason, Long maintenance, long actor, LocalDateTime now) {
        if(bus.getStatus()==status) return;
        db.update("INSERT INTO bus_status_history(bus_id,previous_status,new_status,reason_code,maintenance_id,changed_by,changed_at) VALUES(?,?,?,?,?,?,?)",bus.getId(),bus.getStatus().name(),status.name(),reason,maintenance,actor,JpaJdbcTime.parameter(now));
        bus.setStatus(status);
        buses.saveAndFlush(bus);
    }
    public static BusinessException conflict(String code,String message,Object details) {
        return new BusinessException(code,message,HttpStatus.CONFLICT,details);
    }
    private static ResourceNotFoundException missingBus() { return new ResourceNotFoundException("BUS_NOT_FOUND","Bus was not found."); }
}
