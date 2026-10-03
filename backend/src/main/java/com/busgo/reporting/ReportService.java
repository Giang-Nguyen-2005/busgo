package com.busgo.reporting;

import static com.busgo.reporting.ReportDtos.*;
import com.busgo.common.response.PagedResponse.Pagination;
import com.busgo.common.security.CurrentUser;
import com.busgo.operator.OperatorContextService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('OPERATOR_ADMIN')")
@Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class ReportService {
    private final OperatorContextService context;
    private final ReportRepository queries;
    public ReportService(OperatorContextService context, ReportRepository queries) { this.context=context;this.queries=queries; }
    public Summary summary(CurrentUser user,ReportFilter f) {
        f.validate();long operator=context.requireAdminOperator(user).getId();var asOf=OffsetDateTime.now(ZoneOffset.UTC);
        return new Summary(f.metadata("MIXED_EXPLICIT: collections=paid_at/refunded_at; bookings=created_at; tickets=ticket.created_at; cancellations=cancelled_at; attendance/load/operations=planned_origin_departure",asOf),
                queries.collections(f,operator),queries.bookings(f,operator),queries.cancellations(f,operator),queries.attendance(f,operator),queries.load(f,operator),queries.operations(f,operator));
    }
    public Table<TripPerformance> trips(CurrentUser user,ReportFilter f) {
        f.validate();long operator=context.requireAdminOperator(user).getId();var asOf=OffsetDateTime.now(ZoneOffset.UTC);long count=queries.tripCount(f,operator);
        return new Table<>(f.metadata("PLANNED_ORIGIN_DEPARTURE: money=lifetime_attributed_transactions; bookings/tickets=current_trip_cohort",asOf),queries.trips(f,operator),pagination(f,count));
    }
    public Table<RoutePerformance> routes(CurrentUser user,ReportFilter f) {
        f.validate();long operator=context.requireAdminOperator(user).getId();var asOf=OffsetDateTime.now(ZoneOffset.UTC);long count=queries.routeCount(f,operator);
        return new Table<>(f.metadata("PLANNED_ORIGIN_DEPARTURE: money=lifetime_attributed_transactions; bookings/tickets=current_trip_cohort",asOf),queries.routes(f,operator),pagination(f,count));
    }
    private static Pagination pagination(ReportFilter f,long count) { return new Pagination(f.pageNumber(),f.pageSize(),count,(int)((count+f.pageSize()-1)/f.pageSize())); }
}
