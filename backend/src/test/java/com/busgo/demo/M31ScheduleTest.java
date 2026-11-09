package com.busgo.demo;

import com.busgo.demo.m31.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class M31ScheduleTest {
    @Test void failedRotationRollsBackAndNeverCountsItsSlotsAsCreated() {
        var worker=mock(M31ScheduleBatchService.class);
        var manager=mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        LocalDate date=LocalDate.of(2030,2,2);
        when(worker.apply(eq(date),eq(1),anyString(),eq(true))).thenAnswer(call->{
            String plate=call.getArgument(2);
            if(plate.equals("51B-770.01")) throw new com.busgo.common.exception.BusinessException("BUS_SCHEDULE_CONFLICT","simulated race");
            var legs=M31Schedule.legs(date,1,plate);
            return new M31Schedule.Batch(plate,legs.get(0).from(),true,List.of(),legs.stream()
                .map(l->new M31Schedule.Departure(M31Schedule.key(l),"CREATED",1L,"test",l,"STANDARD40",40,40,java.math.BigDecimal.ONE)).toList(),false);
        });
        var report=new M31ScheduleService(worker,manager).apply(date,1,true);
        assertThat(report.created()).isEqualTo(24); assertThat(report.skipped()).isEqualTo(2);
        assertThat(report.batches()).filteredOn(M31Schedule.Batch::rolledBack).hasSize(1);
        assertThat(report.batches().get(0).departures()).allMatch(d->d.action().equals("SKIP")&&d.reason().contains("BUS_SCHEDULE_CONFLICT"));
        verify(manager).rollback(any()); verify(manager,times(12)).commit(any());
    }
    @Test void matrixHasTwentySixJourneysAndBoundedCompleteInventoryWithStableKeys() {
        var plan=M31Fleet.plan(LocalDate.of(2030,1,2),14);
        var pairs=new HashSet<String>(); var keys=new HashSet<String>(); int inventory=0;
        for(var leg:plan) {
            pairs.add(leg.owner()+":"+leg.route()); assertThat(keys.add(M31Schedule.key(leg))).isTrue();
            assertThat(M31Schedule.price(leg.owner(),leg.route()).amount()).isPositive();
            var type=M31Fleet.vehicles().stream().filter(v->v.plate().equals(leg.plate())).findFirst().orElseThrow().type();
            inventory+=type.equals("LIMO22")?22:type.equals("SLEEPER34")?34:40;
        }
        assertThat(pairs).hasSize(26); assertThat(keys).hasSize(364); assertThat(inventory).isEqualTo(12208);
        assertThat(pairs.stream().filter(p->{int i=p.indexOf(':'); return M31Schedule.price(p.substring(0,i),p.substring(i+1)).legacy();})).hasSize(10);
        assertThat(plan).anyMatch(l->l.arrival().toLocalDate().isAfter(l.departure().toLocalDate()));
        assertThatThrownBy(()->M31Schedule.legs(LocalDate.now(),15,"51B-770.01")).isInstanceOf(IllegalArgumentException.class);
    }
}
