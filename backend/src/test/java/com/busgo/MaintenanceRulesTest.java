package com.busgo;

import static org.assertj.core.api.Assertions.*;
import com.busgo.fleet.*;
import com.busgo.fleet.MaintenanceDtos.*;
import jakarta.validation.Validation;
import java.time.*;
import org.junit.jupiter.api.Test;

class MaintenanceRulesTest {
    @Test void overlapIsHalfOpen() {
        var start=LocalDateTime.of(2030,1,1,0,0);var end=start.plusHours(2);
        assertThat(MaintenanceRules.overlaps(start,end,start.plusHours(1),end.plusHours(1))).isTrue();
        assertThat(MaintenanceRules.overlaps(start,end,end,end.plusHours(1))).isFalse();
        assertThat(MaintenanceRules.overlaps(start,end,start.minusHours(1),start)).isFalse();
        assertThat(MaintenanceRules.overlaps(start,end,start,end)).isTrue();
    }
    @Test void onlyForwardCommandsAllowed() {
        for(var from:Status.values()) for(var to:Status.values())
            assertThat(MaintenanceRules.allowed(from,to)).isEqualTo(from==Status.SCHEDULED && (to==Status.IN_PROGRESS || to==Status.CANCELLED) || from==Status.IN_PROGRESS && to==Status.COMPLETED);
    }
    @Test void dueDatesDoNotInventUnknownOrOdometerOverdue() {
        var today=LocalDate.of(2026,10,4);
        assertThat(MaintenanceRules.overdue(null,today)).isFalse();
        assertThat(MaintenanceRules.overdue(today,today)).isFalse();
        assertThat(MaintenanceRules.overdue(today.minusDays(1),today)).isTrue();
        assertThat(MaintenanceRules.dueSoon(today.plusDays(7),today)).isTrue();
        assertThat(MaintenanceRules.dueSoon(today.plusDays(8),today)).isFalse();
        assertThat(MaintenanceRules.dueSoon(today.minusDays(1),today)).isFalse();
    }
    @Test void boundedInputsAndNonnegativeOdometer() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var v=factory.getValidator();
            assertThat(v.validate(new Schedule(null," ","x".repeat(1001),null,null))).hasSize(5);
            assertThat(v.validate(new Complete(-1L,null,-2L,"x".repeat(1001)))).hasSize(3);
            assertThat(v.validate(new Cancel("x".repeat(501)))).hasSize(1);
        }
    }
    @Test void readinessComposesStatusTypeAndMaintenance() {
        var available=com.busgo.fleet.entity.BusStatus.AVAILABLE;
        assertThat(MaintenanceRules.ready(available,true,false,false)).isTrue();
        for(var status:com.busgo.fleet.entity.BusStatus.values())
            assertThat(MaintenanceRules.ready(status,true,false,false)).isEqualTo(status==available);
        assertThat(MaintenanceRules.ready(available,false,false,false)).isFalse();
        assertThat(MaintenanceRules.ready(available,true,true,false)).isFalse();
        assertThat(MaintenanceRules.ready(available,true,false,true)).isFalse();
    }
}
