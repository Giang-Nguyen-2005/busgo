package com.busgo.reporting;

import com.busgo.booking.entity.BookingSource;
import com.busgo.payment.entity.PaymentMethod;
import com.busgo.common.exception.BusinessException;
import com.busgo.common.time.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/** Inclusive Vietnam calendar dates; all SQL windows are half-open. */
public record ReportFilter(LocalDate fromDate, LocalDate toDate, Long routeId, Long tripId,
        BookingSource bookingSource, PaymentMethod paymentMethod, Integer page, Integer size) {
    public void validate() {
        if (fromDate == null || toDate == null || toDate.isBefore(fromDate)
                || ChronoUnit.DAYS.between(fromDate, toDate) >= 366
                || (routeId != null && routeId <= 0) || (tripId != null && tripId <= 0)
                || (page != null && (page < 0 || page > 100000))
                || (size != null && (size < 1 || size > 100))) {
            throw new BusinessException("INVALID_REPORT_FILTER", "Reports require 1–366 business dates and valid pagination.", HttpStatus.BAD_REQUEST, null);
        }
    }
    public int pageNumber() { return page == null ? 0 : page; }
    public int pageSize() { return size == null ? 20 : size; }
    public MapSqlParameterSource parameters(long operator) {
        return new MapSqlParameterSource("operator", operator)
                .addValue("start", JpaJdbcTime.parameter(BusGoTime.businessDate(fromDate).startInclusive()))
                .addValue("end", JpaJdbcTime.parameter(BusGoTime.businessDate(toDate).endExclusive()))
                .addValue("route", routeId).addValue("trip", tripId)
                .addValue("source", bookingSource == null ? null : bookingSource.name())
                .addValue("method", paymentMethod == null ? null : paymentMethod.name())
                .addValue("limit", pageSize()).addValue("offset", (long)pageNumber() * pageSize());
    }
    public ReportDtos.Metadata metadata(String basis, OffsetDateTime asOf) {
        return new ReportDtos.Metadata(fromDate,toDate,BusGoTime.BUSINESS_ZONE.getId(),asOf,basis,
                routeId,tripId,bookingSource == null ? null : bookingSource.name(),paymentMethod == null ? null : paymentMethod.name());
    }
}
