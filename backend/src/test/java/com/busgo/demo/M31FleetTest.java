package com.busgo.demo;

import com.busgo.demo.m31.M31Fleet;
import java.time.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class M31FleetTest {
    @Test void nineteenVehiclesSupportTwentySixDailyDeparturesWithContinuousPositionAndBuffers() {
        M31Fleet.validate();
        assertThat(M31Fleet.operators()).hasSize(3);
        assertThat(M31Fleet.types()).hasSize(3);
        assertThat(M31Fleet.vehicles()).hasSize(19);
        assertThat(M31Fleet.vehicles().stream().filter(M31Fleet.Vehicle::legacy)).hasSize(7);
        assertThat(M31Fleet.vehicles().stream().filter(v->v.purpose().equals("SPARE"))).hasSize(5);
        var plan=M31Fleet.plan(LocalDate.of(2030,1,1),14);
        assertThat(plan).hasSize(364);
        assertThat(plan.stream().collect(Collectors.groupingBy(l->l.departure().toLocalDate(),Collectors.counting())).values())
                .hasSize(14).allMatch(count->count==26);
        assertThat(plan).anyMatch(l->l.arrival().toLocalDate().isAfter(l.departure().toLocalDate()));
        assertThat(plan).noneMatch(l->l.plate().equals("51B-770.03"));
        assertThatThrownBy(()->M31Fleet.plan(LocalDate.now(),15)).isInstanceOf(IllegalArgumentException.class);
    }
}
