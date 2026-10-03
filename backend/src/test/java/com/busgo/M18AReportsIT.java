package com.busgo;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.busgo.reporting.*;
import com.busgo.booking.*;
import com.busgo.booking.AssistedBookingDtos.*;
import com.busgo.booking.entity.BookingSource;
import com.busgo.payment.PaymentTicketService;
import com.busgo.payment.entity.PaymentMethod;
import com.busgo.common.time.*;
import com.busgo.user.entity.RoleCode;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev") @Transactional
class M18AReportsIT extends M16ATestSupport {
    @Autowired ReportService reports;
    @Autowired ReportRepository reportQueries;
    @Autowired CancellationService cancellations;
    @Autowired PaymentTicketService paymentService;
    @Autowired MockMvc mvc;
    @AfterEach void clear() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    private LocalDate today() { return LocalDate.now(BusGoTime.BUSINESS_ZONE); }
    private ReportFilter transaction(Fixture f) { return new ReportFilter(today(),today(),null,f.trip().getId(),null,null,0,20); }
    private ReportFilter travel(Fixture f) { var d=f.trip().getDepartureTime().atOffset(ZoneOffset.UTC).atZoneSameInstant(BusGoTime.BUSINESS_ZONE).toLocalDate();return new ReportFilter(d,d,null,f.trip().getId(),null,null,0,20); }
    private CreateRequest multi(Fixture f,int from,int to,PaymentMethod method) { return new CreateRequest(f.trip().getId(),f.locations().get(from).getId(),f.locations().get(to).getId(),f.seats().stream().map(s->s.getId()).toList(),"Report customer","0901234567",null,method); }

    @Test void multiSeatPaymentsAndRefundsNeverMultiplyAndEachMethodHasItsOwnGrain() {
        var f=fixture();var admin=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(admin);
        var qr=assisted.create(admin,multi(f,0,1,PaymentMethod.QR_TRANSFER));assisted.publicConfirm(token(assisted.issueLink(admin,qr.bookingId())));
        var cash=assisted.create(admin,multi(f,1,2,PaymentMethod.PAY_ON_BOARD));assisted.record(admin,cash.bookingId(),new RecordPayment(PaymentMethod.PAY_ON_BOARD,null));
        var customer=customer("m18-web");authenticate(customer.user());
        var web=bookingService.create(customer.user(),new BookingDtos.CreateBookingRequest(hold(customer,f,2,3,0,1).holdToken(),"Web","0901234567","web@example.test"));paymentService.confirm(customer.user(),web.bookingId());
        authenticate(admin);cancellations.operatorCancel(admin,qr.bookingId(),new CancellationDtos.Request(null));
        var report=reports.summary(admin,transaction(f));var m=report.collections().totals();
        assertThat(m.grossMockCollections()).isEqualByComparingTo("600");assertThat(m.mockRefunds()).isEqualByComparingTo("200");assertThat(m.netMockCollections()).isEqualByComparingTo("400");
        assertThat(m.paidPaymentCount()).isEqualTo(3);assertThat(m.refundedPaymentCount()).isOne();
        for(var method:PaymentMethod.values()) assertThat(report.collections().byPaymentMethod().get(method.name()).grossMockCollections()).isEqualByComparingTo("200");
        assertThat(report.bookings().bookingsCreated()).isEqualTo(3);assertThat(report.bookings().ticketsIssued()).isEqualTo(6);assertThat(report.bookings().validTickets()).isEqualTo(4);assertThat(report.bookings().voidTickets()).isEqualTo(2);
        assertThat(report.bookings().bySource()).containsEntry("WEB",1L).containsEntry("PHONE",2L);
        assertThat(report.cancellations().refundedCancellations()).isOne();assertThat(report.cancellations().amountRefunded()).isEqualByComparingTo("200");
        var trips=reports.trips(admin,travel(f));assertThat(trips.data()).hasSize(1);assertThat(trips.data().get(0).money()).isEqualTo(m);
        var routes=reports.routes(admin,travel(f));assertThat(routes.data()).hasSize(1);assertThat(routes.data().get(0).money()).isEqualTo(m);
    }
    @Test void refundTodayDoesNotRewriteLastMonthsGrossAndNetCanBeNegative() {
        var f=fixture();var admin=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(admin);
        var b=assisted.create(admin,multi(f,0,2,PaymentMethod.PAY_ON_BOARD));assisted.record(admin,b.bookingId(),new RecordPayment(PaymentMethod.PAY_ON_BOARD,null));
        var past=today().minusMonths(1);jdbc.update("UPDATE payments SET paid_at=? WHERE booking_id=?",JpaJdbcTime.parameter(BusGoTime.businessTime(past,LocalTime.NOON)),b.bookingId());em.clear();
        cancellations.operatorCancel(admin,b.bookingId(),new CancellationDtos.Request(null));
        var old=reports.summary(admin,new ReportFilter(past,past,null,f.trip().getId(),null,null,0,20)).collections().totals();
        var now=reports.summary(admin,transaction(f)).collections().totals();
        assertThat(old.grossMockCollections()).isEqualByComparingTo("400");assertThat(old.mockRefunds()).isZero();
        assertThat(now.grossMockCollections()).isZero();assertThat(now.mockRefunds()).isEqualByComparingTo("400");assertThat(now.netMockCollections()).isEqualByComparingTo("-400");
        assertThat(now.paidPaymentCount()).isZero();assertThat(now.refundedPaymentCount()).isOne();
    }
    @Test void localMidnightJustBeforeMidnightAndMonthBoundaryBindLikeJpa() {
        var f=fixture();var admin=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(admin);
        var b=assisted.create(admin,request(f,0,1,PaymentMethod.PAY_ON_BOARD));assisted.record(admin,b.bookingId(),new RecordPayment(PaymentMethod.PAY_ON_BOARD,null));
        var d=LocalDate.of(2026,10,1);var w=BusGoTime.businessDate(d);var filter=new ReportFilter(d,d,null,f.trip().getId(),null,null,0,20);
        for(var time:List.of(w.startInclusive().minusNanos(1000),w.startInclusive(),w.endExclusive().minusNanos(1000),w.endExclusive())) {
            jdbc.update("UPDATE payments SET paid_at=? WHERE booking_id=?",JpaJdbcTime.parameter(time),b.bookingId());
            var amount=reports.summary(admin,filter).collections().totals().grossMockCollections();
            assertThat(amount).isEqualByComparingTo(time.isBefore(w.startInclusive())||!time.isBefore(w.endExclusive())?BigDecimal.ZERO:new BigDecimal("100"));
        }
    }
    @Test void nonOverlappingSeatReuseUnpaidReservationsBlockedAndHeldHaveDistinctLoads() {
        var f=fixture();var admin=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(admin);
        var paid=assisted.create(admin,request(f,0,1,PaymentMethod.PAY_ON_BOARD));assisted.record(admin,paid.bookingId(),new RecordPayment(PaymentMethod.PAY_ON_BOARD,null));
        assisted.create(admin,request(f,1,3,PaymentMethod.PAY_ON_BOARD));
        jdbc.update("UPDATE trip_seat_segment_inventory SET status='BLOCKED' WHERE trip_seat_id=? AND trip_segment_id=?",f.seats().get(1).getId(),f.segments().get(0).getId());
        var owner=customer("m18-hold");hold(owner,f,1,2,1);
        var load=reports.summary(admin,travel(f)).load();assertThat(load.complete()).isTrue();assertThat(load.expectedCells()).isEqualTo(6);assertThat(load.sellableCells()).isEqualTo(5);assertThat(load.reservedCells()).isEqualTo(3);assertThat(load.paidCells()).isOne();assertThat(load.heldCells()).isOne();assertThat(load.paidSegmentLoad()).isEqualTo(.2);assertThat(load.reservedSegmentLoad()).isEqualTo(.6);
        var filtered=new ReportFilter(travel(f).fromDate(),travel(f).toDate(),null,f.trip().getId(),BookingSource.WEB,null,0,20);
        assertThat(reports.summary(admin,filtered).load().sellableCells()).isEqualTo(5);assertThat(reports.summary(admin,filtered).load().reservedCells()).isZero();
        cancellations.operatorCancel(admin,paid.bookingId(),new CancellationDtos.Request(null));load=reports.summary(admin,travel(f)).load();assertThat(load.paidCells()).isZero();assertThat(load.reservedCells()).isEqualTo(2);
    }
    @Test void absentCellAndAbsentSegmentNeverProduceFalsePercentagesOrWholeTripAvailability() {
        var f=fixture();var admin=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(admin);
        jdbc.update("DELETE FROM trip_seat_segment_inventory WHERE trip_seat_id=? AND trip_segment_id=?",f.seats().get(0).getId(),f.segments().get(0).getId());
        var r=reports.summary(admin,travel(f));assertThat(r.load().expectedCells()).isEqualTo(6);assertThat(r.load().actualCells()).isEqualTo(5);assertThat(r.load().missingCells()).isOne();assertThat(r.load().complete()).isFalse();assertThat(r.load().reservedSegmentLoad()).isNull();assertThat(r.load().paidSegmentLoad()).isNull();assertThat(r.operations().incompleteTrips()).isOne();assertThat(reports.trips(admin,travel(f)).data().get(0).wholeTripAvailableSeats()).isNull();assertThat(reports.routes(admin,travel(f)).data().get(0).load().paidSegmentLoad()).isNull();
        jdbc.update("DELETE FROM trip_seat_segment_inventory WHERE trip_segment_id=?",f.segments().get(2).getId());jdbc.update("DELETE FROM trip_segments WHERE id=?",f.segments().get(2).getId());
        assertThat(reports.summary(admin,travel(f)).load().complete()).isFalse();
    }
    @Test void routeLoadIsSumOfCellsNotAverageOfTripPercentagesAndEmptyRoutesRemainVisible() {
        var f=fixture();var other=fixture();var admin=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(admin);
        var first=assisted.create(admin,request(f,0,3,PaymentMethod.PAY_ON_BOARD));assisted.record(admin,first.bookingId(),new RecordPayment(PaymentMethod.PAY_ON_BOARD,null));
        // Move a fresh fixture trip onto the same owned route: two trips with different sellable capacities.
        jdbc.update("UPDATE trips SET operator_route_id=? WHERE id=?",f.operatorRoute().getId(),other.trip().getId());
        jdbc.update("UPDATE trip_seat_segment_inventory SET status='BLOCKED' WHERE trip_seat_id=?",other.seats().get(1).getId());
        var filter=new ReportFilter(travel(f).fromDate(),travel(f).toDate(),f.route().getId(),null,null,null,0,20);
        var row=reports.routes(admin,filter).data().get(0);assertThat(row.tripCount()).isEqualTo(2);assertThat(row.load().sellableCells()).isEqualTo(9);assertThat(row.load().paidCells()).isEqualTo(3);assertThat(row.load().paidSegmentLoad()).isEqualTo(1.0/3);assertThat(row.load().paidSegmentLoad()).isNotEqualTo(.25);
        var page=reports.trips(admin,new ReportFilter(filter.fromDate(),filter.toDate(),filter.routeId(),null,null,null,1,1));assertThat(page.data()).hasSize(1);assertThat(page.pagination().totalElements()).isEqualTo(2);assertThat(page.pagination().totalPages()).isEqualTo(2);
        var empty=new ReportFilter(today(),today(),f.route().getId(),null,null,null,0,20);assertThat(reports.routes(admin,empty).data().get(0).tripCount()).isZero();
    }
    @Test void cancelledTripIsVisibleButExcludedFromWeightedRouteAndSummaryLoad() {
        var normal=fixture();var cancelled=fixture();var admin=actor(normal,RoleCode.OPERATOR_ADMIN);authenticate(admin);
        var first=assisted.create(admin,request(normal,0,3,PaymentMethod.PAY_ON_BOARD));assisted.record(admin,first.bookingId(),new RecordPayment(PaymentMethod.PAY_ON_BOARD,null));
        var otherAdmin=actor(cancelled,RoleCode.OPERATOR_ADMIN);authenticate(otherAdmin);
        var second=assisted.create(otherAdmin,multi(cancelled,0,3,PaymentMethod.PAY_ON_BOARD));assisted.record(otherAdmin,second.bookingId(),new RecordPayment(PaymentMethod.PAY_ON_BOARD,null));
        jdbc.update("UPDATE trips SET operator_route_id=?,bus_id=? WHERE id=?",normal.operatorRoute().getId(),normal.bus().getId(),cancelled.trip().getId());
        authenticate(admin);
        var filter=new ReportFilter(travel(normal).fromDate(),travel(normal).toDate(),normal.route().getId(),null,null,null,0,20);
        var before=reports.routes(admin,filter).data().get(0);assertThat(before.load().paidSegmentLoad()).isEqualTo(.75);
        jdbc.update("UPDATE trips SET status='CANCELLED' WHERE id=?",cancelled.trip().getId());
        var route=reports.routes(admin,filter).data().get(0);var summary=reports.summary(admin,filter);
        assertThat(route.tripCount()).isEqualTo(2);assertThat(summary.operations().trips()).isEqualTo(2);
        assertThat(route.money()).isEqualTo(before.money());assertThat(route.bookings()).isEqualTo(before.bookings());assertThat(route.validTickets()).isEqualTo(before.validTickets());
        assertThat(route.load()).isEqualTo(summary.load());assertThat(route.load().expectedCells()).isEqualTo(6);assertThat(route.load().sellableCells()).isEqualTo(6);
        assertThat(route.load().reservedCells()).isEqualTo(3);assertThat(route.load().paidCells()).isEqualTo(3);assertThat(route.load().paidSegmentLoad()).isEqualTo(.5);assertThat(route.load().reservedSegmentLoad()).isEqualTo(.5);
        var rows=reports.trips(admin,filter);assertThat(rows.pagination().totalElements()).isEqualTo(2);assertThat(rows.data()).extracting(r->r.status()).containsExactlyInAnyOrder("SCHEDULED","CANCELLED");
        var cancelledRow=rows.data().stream().filter(r->r.tripId()==cancelled.trip().getId()).findFirst().orElseThrow();
        assertThat(cancelledRow.load().paidCells()).isEqualTo(6);assertThat(cancelledRow.load().complete()).isTrue();assertThat(cancelledRow.load().paidSegmentLoad()).isNull();assertThat(cancelledRow.load().reservedSegmentLoad()).isNull();
        // A cancelled trip's missing inventory cannot poison the contributing trip's completeness.
        jdbc.update("DELETE FROM trip_seat_segment_inventory WHERE trip_seat_id=? AND trip_segment_id=?",cancelled.seats().get(0).getId(),cancelled.segments().get(0).getId());
        assertThat(reports.routes(admin,filter).data().get(0).load()).isEqualTo(route.load());assertThat(reports.summary(admin,filter).load()).isEqualTo(summary.load());
        // Missing inventory on the non-cancelled contributor must still suppress aggregate ratios.
        jdbc.update("DELETE FROM trip_seat_segment_inventory WHERE trip_seat_id=? AND trip_segment_id=?",normal.seats().get(1).getId(),normal.segments().get(0).getId());
        assertThat(reports.routes(admin,filter).data().get(0).load().complete()).isFalse();assertThat(reports.routes(admin,filter).data().get(0).load().paidSegmentLoad()).isNull();
        assertThat(reports.summary(admin,filter).load().missingCells()).isOne();assertThat(reports.summary(admin,filter).load().reservedSegmentLoad()).isNull();
    }
    @Test void allCancelledRouteHasNoContributingCapacityAndNullLoadInsteadOfZeroPercent() {
        var f=fixture();var admin=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(admin);
        var b=assisted.create(admin,multi(f,0,2,PaymentMethod.PAY_ON_BOARD));assisted.record(admin,b.bookingId(),new RecordPayment(PaymentMethod.PAY_ON_BOARD,null));
        jdbc.update("UPDATE trips SET status='CANCELLED' WHERE id=?",f.trip().getId());
        var summary=reports.summary(admin,travel(f));var route=reports.routes(admin,travel(f)).data().get(0);var row=reports.trips(admin,travel(f)).data().get(0);
        assertThat(summary.operations().trips()).isOne();assertThat(route.tripCount()).isOne();assertThat(row.status()).isEqualTo("CANCELLED");
        assertThat(summary.load()).isEqualTo(route.load());assertThat(route.load().expectedCells()).isZero();assertThat(route.load().actualCells()).isZero();assertThat(route.load().sellableCells()).isZero();assertThat(route.load().reservedCells()).isZero();assertThat(route.load().paidCells()).isZero();assertThat(route.load().complete()).isTrue();
        assertThat(route.load().paidSegmentLoad()).isNull();assertThat(route.load().reservedSegmentLoad()).isNull();assertThat(row.load().paidSegmentLoad()).isNull();assertThat(row.load().reservedSegmentLoad()).isNull();assertThat(row.load().expectedCells()).isEqualTo(6);
        assertThat(route.money().grossMockCollections()).isEqualByComparingTo("400");assertThat(route.bookings()).isOne();assertThat(route.validTickets()).isEqualTo(2);
    }
    @Test void tripLoadExclusionDoesNotChangeTransactionMoneyRefundCancellationOrIssuanceCohorts() {
        var f=fixture();var admin=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(admin);
        var paid=assisted.create(admin,multi(f,0,2,PaymentMethod.PAY_ON_BOARD));assisted.record(admin,paid.bookingId(),new RecordPayment(PaymentMethod.PAY_ON_BOARD,null));
        cancellations.operatorCancel(admin,paid.bookingId(),new CancellationDtos.Request(null));
        var before=reports.summary(admin,transaction(f));var routeBefore=reports.routes(admin,travel(f)).data().get(0);
        jdbc.update("UPDATE trips SET status='CANCELLED' WHERE id=?",f.trip().getId());
        var after=reports.summary(admin,transaction(f));var routeAfter=reports.routes(admin,travel(f)).data().get(0);
        assertThat(after.collections()).isEqualTo(before.collections());assertThat(after.bookings()).isEqualTo(before.bookings());assertThat(after.cancellations()).isEqualTo(before.cancellations());
        assertThat(after.collections().totals().grossMockCollections()).isEqualByComparingTo("400");assertThat(after.collections().totals().mockRefunds()).isEqualByComparingTo("400");assertThat(after.collections().totals().netMockCollections()).isZero();
        assertThat(after.bookings().bookingsCreated()).isOne();assertThat(after.bookings().ticketsIssued()).isEqualTo(2);assertThat(after.bookings().voidTickets()).isEqualTo(2);assertThat(after.cancellations().totalCancellations()).isOne();
        assertThat(routeAfter.money()).isEqualTo(routeBefore.money());assertThat(routeAfter.bookings()).isEqualTo(routeBefore.bookings());assertThat(routeAfter.tripCount()).isEqualTo(routeBefore.tripCount());
    }
    @Test void attendanceUsesResolvedValidTicketsSeparatesCheckedInAndTicketlessAndIgnoresCancelled() {
        var f=fixture();var admin=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(admin);
        var paid=assisted.create(admin,multi(f,0,1,PaymentMethod.PAY_ON_BOARD));assisted.record(admin,paid.bookingId(),new RecordPayment(PaymentMethod.PAY_ON_BOARD,null));
        var ids=jdbc.queryForList("SELECT id FROM tickets WHERE booking_id=? ORDER BY id",Long.class,paid.bookingId());
        jdbc.update("UPDATE ticket_boarding SET status='BOARDED' WHERE ticket_id=?",ids.get(0));jdbc.update("UPDATE ticket_boarding SET status='NO_SHOW' WHERE ticket_id=?",ids.get(1));
        var checked=assisted.create(admin,multi(f,1,2,PaymentMethod.PAY_ON_BOARD));assisted.record(admin,checked.bookingId(),new RecordPayment(PaymentMethod.PAY_ON_BOARD,null));
        var checks=jdbc.queryForList("SELECT id FROM tickets WHERE booking_id=? ORDER BY id",Long.class,checked.bookingId());
        jdbc.update("UPDATE ticket_boarding SET status='CHECKED_IN' WHERE ticket_id=?",checks.get(0));jdbc.update("DELETE FROM ticket_boarding WHERE ticket_id=?",checks.get(1));
        var unpaid=assisted.create(admin,multi(f,2,3,PaymentMethod.PAY_ON_BOARD));
        jdbc.update("INSERT INTO ticket_boarding(booking_item_id,status,pickup_stop_id) SELECT id,'NO_SHOW',? FROM booking_items WHERE booking_id=?",f.stops().get(2).getId(),unpaid.bookingId());
        var a=reports.summary(admin,travel(f)).attendance();assertThat(a.eligibleResolvedTickets()).isEqualTo(2);assertThat(a.boardedTickets()).isOne();assertThat(a.noShowTickets()).isOne();assertThat(a.checkedInNotBoarded()).isOne();assertThat(a.unresolvedAttendance()).isOne();assertThat(a.ticketlessNoShows()).isEqualTo(2);assertThat(a.boardingRate()).isEqualTo(.5);assertThat(a.noShowRate()).isEqualTo(.5);
        jdbc.update("UPDATE bookings SET status='CANCELLED' WHERE id=?",paid.bookingId());a=reports.summary(admin,travel(f)).attendance();assertThat(a.boardedTickets()).isZero();assertThat(a.noShowTickets()).isZero();assertThat(a.boardingRate()).isNull();
    }
    @Test void structuredUnpaidCancellationAndTimeoutAreNotRefunds() {
        var f=fixture();var admin=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(admin);
        var b=assisted.create(admin,request(f,0,1,PaymentMethod.PAY_ON_BOARD));cancellations.operatorCancel(admin,b.bookingId(),new CancellationDtos.Request(null));
        var timeout=assisted.create(admin,request(f,1,2,PaymentMethod.QR_TRANSFER));jdbc.update("UPDATE bookings SET payment_due_at=? WHERE id=?",JpaJdbcTime.parameter(BusGoTime.utc(Instant.now().minusSeconds(60))),timeout.bookingId());em.clear();assertThat(cancellations.expire(timeout.bookingId())).isTrue();
        var r=reports.summary(admin,transaction(f));assertThat(r.cancellations().byReason()).containsEntry("PAYMENT_TIMEOUT",1L).containsEntry("OPERATOR_CANCELLED",1L);assertThat(r.cancellations().totalCancellations()).isEqualTo(2);assertThat(r.cancellations().unpaidCancellations()).isEqualTo(2);assertThat(r.cancellations().refundedCancellations()).isZero();assertThat(r.collections().totals().mockRefunds()).isZero();assertThat(r.bookings().paymentTimeouts()).isOne();
    }
    @Test void everyEndpointDeniesStaffCustomersSystemAdminAnonymousAndForeignContributions() throws Exception {
        var f=fixture();var foreign=fixture();var admin=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(admin);
        var b=assisted.create(admin,request(f,0,1,PaymentMethod.PAY_ON_BOARD));assisted.record(admin,b.bookingId(),new RecordPayment(PaymentMethod.PAY_ON_BOARD,null));
        var otherAdmin=actor(foreign,RoleCode.OPERATOR_ADMIN);authenticate(otherAdmin);
        var other=assisted.create(otherAdmin,request(foreign,0,1,PaymentMethod.PAY_ON_BOARD));assisted.record(otherAdmin,other.bookingId(),new RecordPayment(PaymentMethod.PAY_ON_BOARD,null));
        authenticate(admin);assertThat(reports.summary(admin,transaction(foreign)).collections().totals().grossMockCollections()).isZero();assertThat(reports.trips(admin,travel(foreign)).data()).isEmpty();assertThat(reports.routes(admin,travel(foreign)).data()).isEmpty();
        for(String endpoint:List.of("summary","trips","routes")) {
            String path="/api/v1/operator/reports/"+endpoint+"?fromDate="+today()+"&toDate="+today();
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            for(var denied:List.of(actor(f,RoleCode.OPERATOR_STAFF),actor(f,RoleCode.SYSTEM_ADMIN),customer("m18-denied").user())) mvc.perform(get(path).header("Authorization",bearer(denied))).andExpect(status().isForbidden());
            mvc.perform(get(path).header("Authorization",bearer(admin))).andExpect(status().isOk()).andExpect(jsonPath("data.metadata.timezone").value("Asia/Ho_Chi_Minh"));
            mvc.perform(get("/api/v1/operator/reports/"+endpoint+"?fromDate=2025-01-01&toDate=2026-10-03").header("Authorization",bearer(admin))).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/operator/reports/summary?fromDate=bad&toDate=2026-10-03").header("Authorization",bearer(admin))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/operator/reports/summary").header("Authorization",bearer(admin))).andExpect(status().isBadRequest());
    }
    @Test void representativeOneThirtyAnd365DayQueriesAreProfiledWithExplain() {
        var f=fixture();var admin=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(admin);
        var b=assisted.create(admin,multi(f,0,2,PaymentMethod.PAY_ON_BOARD));assisted.record(admin,b.bookingId(),new RecordPayment(PaymentMethod.PAY_ON_BOARD,null));
        jdbc.update("UPDATE payments SET paid_at=? WHERE booking_id=?",JpaJdbcTime.parameter(BusGoTime.businessTime(travel(f).toDate(),LocalTime.NOON)),b.bookingId());
        jdbc.update("UPDATE bookings SET created_at=? WHERE id=?",JpaJdbcTime.parameter(BusGoTime.businessTime(travel(f).toDate(),LocalTime.NOON)),b.bookingId());
        for(int days:List.of(1,30,365)) {
            var end=travel(f).toDate();var filter=new ReportFilter(end.minusDays(days-1),end,null,null,null,null,0,20);
            long start=System.nanoTime();reports.summary(admin,filter);reports.trips(admin,filter);reports.routes(admin,filter);
            System.out.println("M18A PROFILE days="+days+" elapsedMs="+(System.nanoTime()-start)/1_000_000);
            var plan=reportQueries.explainTrips(filter,f.operator().getId());assertThat(plan).isNotEmpty();System.out.println("M18A EXPLAIN days="+days+" "+plan);
            var routesPlan=reportQueries.explainRoutes(filter,f.operator().getId());assertThat(routesPlan).isNotEmpty();System.out.println("M18A ROUTE EXPLAIN days="+days+" "+routesPlan);
        }
    }
}
