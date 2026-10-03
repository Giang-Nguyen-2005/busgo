package com.busgo;

import com.busgo.reporting.*;
import com.busgo.common.time.BusGoTime;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReportFilterTest {
    @Test void vietnamMidnightAndMonthBoundaryUseUtcHalfOpenWindows() {
        var date=LocalDate.of(2026,10,1);var w=BusGoTime.businessDate(date);
        assertThat(w.startInclusive()).isEqualTo(LocalDateTime.of(2026,9,30,17,0));
        assertThat(w.endExclusive()).isEqualTo(LocalDateTime.of(2026,10,1,17,0));
        assertThat(w.startInclusive().atOffset(ZoneOffset.UTC).atZoneSameInstant(BusGoTime.BUSINESS_ZONE).toLocalDate()).isEqualTo(date);
    }
    @Test void rangeIsRequiredOrderedAndAtMost366InclusiveDays() {
        var start=LocalDate.of(2026,1,1);
        new ReportFilter(start,start.plusDays(365),null,null,null,null,null,null).validate();
        for(var filter:new ReportFilter[]{new ReportFilter(null,start,null,null,null,null,null,null),new ReportFilter(start,start.minusDays(1),null,null,null,null,null,null),new ReportFilter(start,start.plusDays(366),null,null,null,null,null,null),new ReportFilter(start,start,null,null,null,null,0,101)})
            assertThatThrownBy(filter::validate).isInstanceOf(com.busgo.common.exception.BusinessException.class);
        assertThat(ReportRepository.ratio(0,0)).isNull();assertThat(ReportRepository.ratio(1,4)).isEqualTo(.25);
    }
}
