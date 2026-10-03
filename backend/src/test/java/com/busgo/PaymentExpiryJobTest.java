package com.busgo;

import com.busgo.booking.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentExpiryJobTest {
    @Test void invalidBatchConfigurationFailsAtStartup() {
        for(int size:new int[]{0,-1,1001}) assertThatThrownBy(()->new PaymentExpiryJob(mock(CancellationService.class),mock(JdbcTemplate.class),Clock.systemUTC(),size)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void perBookingFailureDoesNotPreventLaterRecoveryAndOnlyCommittedExpiriesCount() {
        var cancellations=mock(CancellationService.class); var db=mock(JdbcTemplate.class);
        when(db.queryForList(anyString(),eq(Long.class),any(),eq(3))).thenReturn(List.of(1L,2L,3L));
        when(cancellations.expire(1L)).thenThrow(new IllegalStateException("Corrupt inventory"));
        when(cancellations.expire(2L)).thenReturn(false); when(cancellations.expire(3L)).thenReturn(true);
        assertThat(new PaymentExpiryJob(cancellations,db,Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"),ZoneOffset.UTC),3).expirePending()).isOne();
        verify(cancellations).expire(1L); verify(cancellations).expire(2L); verify(cancellations).expire(3L);
    }
}
