package com.busgo;

import com.busgo.fleet.*;
import com.busgo.fleet.entity.*;
import com.busgo.common.entity.ActiveStatus;
import java.time.Clock;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FleetReadBatchTest {
    @Test void oneHundredBusesUseTwoPlanningReadsAndNoPerTripGuardQueries() {
        var db=mock(JdbcTemplate.class);
        var guard=mock(FleetMaintenanceGuard.class);
        var service=new MaintenanceService(db,null,guard,null,Clock.systemUTC());
        var type=new BusType();type.setStatus(ActiveStatus.ACTIVE);
        var buses=LongStream.rangeClosed(1,100).mapToObj(id->{
            var bus=new Bus();org.springframework.test.util.ReflectionTestUtils.setField(bus,"id",id);bus.setBusType(type);bus.setStatus(BusStatus.AVAILABLE);return bus;
        }).toList();
        var result=service.readiness(7,buses);
        assertThat(result).hasSize(100);
        assertThat(result.values()).allMatch(MaintenanceDtos.Readiness::operationalReady);
        assertThat(mockingDetails(db).getInvocations()).hasSize(2);
        verifyNoInteractions(guard);
        clearInvocations(db);
        assertThat(service.readiness(7,java.util.List.of())).isEmpty();
        verifyNoInteractions(db);
    }
}
