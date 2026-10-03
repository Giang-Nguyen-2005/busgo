package com.busgo;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.busgo.booking.*;
import com.busgo.booking.AssistedBookingDtos.*;
import com.busgo.common.exception.BusinessException;
import com.busgo.common.security.CurrentUser;
import com.busgo.payment.PaymentTicketService;
import com.busgo.payment.entity.PaymentMethod;
import com.busgo.user.entity.RoleCode;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class M17CancellationIT extends M16ATestSupport {
    @Autowired CancellationService cancellations;
    @Autowired PaymentTicketService paymentService;
    @Autowired MockMvc mvc;
    @Autowired com.busgo.operations.OperationsService ops;
    private final CancellationDtos.Request reason=new CancellationDtos.Request("M17 test");
    @AfterEach void clear() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }

    @Test void unpaidPhoneMethodsCancelWithoutCommerceAndLeaveOtherAllocationsUntouched() {
        for(var method:List.of(PaymentMethod.PAY_ON_BOARD,PaymentMethod.QR_TRANSFER)) {
            var f=fixture(); var admin=actor(f,RoleCode.OPERATOR_ADMIN); authenticate(admin);
            var b=assisted.create(admin,request(f,0,2,method));
            var other=assisted.create(admin,request(f,2,3,method));
            var owner=customer("m17-held"); var held=hold(owner,f,0,1,1);
            jdbc.update("UPDATE trip_seat_segment_inventory SET status='BLOCKED' WHERE trip_seat_id=? AND trip_segment_id=?",f.seats().get(1).getId(),f.segments().get(2).getId());
            var result=cancellations.operatorCancel(admin,b.bookingId(),reason);
            assertThat(result.status().name()).isEqualTo("CANCELLED"); assertThat(result.refunds()).isEmpty();
            assertThat(result.tickets()).isEmpty(); assertThat(result.history()).hasSize(1);
            assertThat(count("payments",b.bookingId())).isZero();
            assertThat(cancellations.operatorCancel(admin,b.bookingId(),reason).cancelledAt()).isEqualTo(result.cancelledAt());
            assertThat(count("booking_status_history",b.bookingId())).isOne();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory v JOIN booking_items i ON i.id=v.booking_item_id WHERE i.booking_id=? AND v.status='BOOKED'",Integer.class,other.bookingId())).isOne();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory WHERE hold_token=? AND status='HELD'",Integer.class,held.holdToken())).isOne();
            assertThat(jdbc.queryForObject("SELECT status FROM trip_seat_segment_inventory WHERE trip_seat_id=? AND trip_segment_id=?",String.class,f.seats().get(1).getId(),f.segments().get(2).getId())).isEqualTo("BLOCKED");
            assertThat(hold(owner,f,0,2,0).status().name()).isEqualTo("ACTIVE");
        }
    }

    @Test void paidCancellationHasExactlyOneFullRefundPreservesPaidTimestampAndVoidsTickets() {
        var f=fixture(); var admin=actor(f,RoleCode.OPERATOR_ADMIN); authenticate(admin);
        var b=assisted.create(admin,request(f,0,2,PaymentMethod.QR_TRANSFER));
        String token=token(assisted.issueLink(admin,b.bookingId())); assisted.publicConfirm(token);
        var before=jdbc.queryForMap("SELECT amount,paid_at FROM payments WHERE booking_id=?",b.bookingId());
        var result=cancellations.operatorCancel(admin,b.bookingId(),reason);
        assertThat(result.refunds()).hasSize(1); assertThat(result.refunds().get(0).amount()).isEqualByComparingTo(b.totalAmount());
        assertThat(result.tickets()).allMatch(t->t.status().equals("VOID"));
        assertThat(jdbc.queryForMap("SELECT amount,paid_at FROM payments WHERE booking_id=?",b.bookingId())).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE booking_id=?",String.class,b.bookingId())).isEqualTo("REFUNDED");
        cancellations.operatorCancel(admin,b.bookingId(),reason);
        assertThat(result.history()).hasSize(2); assertThat(count("booking_status_history",b.bookingId())).isEqualTo(2);
        assertThatThrownBy(()->assisted.publicConfirm(token)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getCode()).isEqualTo("BOOKING_NOT_PAYABLE"));
        assertThatThrownBy(()->ops.transition(admin,f.trip().getId(),result.tickets().get(0).id(),"check-in",new com.busgo.operations.OperationsDtos.PickupContext(f.stops().get(0).getId(),null)))
                .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getCode()).isEqualTo("TICKET_NOT_ELIGIBLE"));
        assertThatThrownBy(()->jdbc.update("INSERT INTO refunds(payment_id,amount,payment_paid_at,refunded_at,reason_code,created_at) SELECT id,amount,paid_at,UTC_TIMESTAMP(6),'OPERATOR_CANCELLED',UTC_TIMESTAMP(6) FROM payments WHERE id=?",result.refunds().get(0).paymentId())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void missingAllocationRollsBackPaidCancellationBeforeAnyReleaseOrRefund() {
        var f=fixture(); var admin=actor(f,RoleCode.OPERATOR_ADMIN); authenticate(admin);
        var b=assisted.create(admin,new CreateRequest(f.trip().getId(),f.locations().get(0).getId(),f.locations().get(2).getId(),f.seats().stream().map(s->s.getId()).toList(),"Multi","0901234567",null,PaymentMethod.PAY_ON_BOARD));
        assisted.record(admin,b.bookingId(),new RecordPayment(PaymentMethod.PAY_ON_BOARD,null));
        jdbc.update("DELETE FROM trip_seat_segment_inventory WHERE trip_seat_id=? AND trip_segment_id=?",f.seats().get(1).getId(),f.segments().get(1).getId());
        assertThatThrownBy(()->cancellations.operatorCancel(admin,b.bookingId(),reason)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getCode()).isEqualTo("BOOKING_INVENTORY_INCONSISTENT"));
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,b.bookingId())).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory v JOIN booking_items i ON i.id=v.booking_item_id WHERE i.booking_id=? AND v.status='BOOKED'",Integer.class,b.bookingId())).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refunds r JOIN payments p ON p.id=r.payment_id WHERE p.booking_id=?",Integer.class,b.bookingId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE booking_id=? AND status='VALID'",Integer.class,b.bookingId())).isEqualTo(2);
    }

    @Test void customerCutoffUsesIntermediatePickupAndUnpaidSuspensionRecoveryWorks() {
        var f=fixture(); var owner=customer("m17-web"); authenticate(owner.user());
        var b=bookingService.create(owner.user(),new BookingDtos.CreateBookingRequest(hold(owner,f,2,3,0).holdToken(),"Web","0901234567","web@example.test"));
        LocalDateTime now=LocalDateTime.now(ZoneOffset.UTC);
        jdbc.update("UPDATE trip_stops SET planned_departure_time=? WHERE id=?",com.busgo.common.time.JpaJdbcTime.parameter(now.plusHours(7)),f.stops().get(2).getId());
        jdbc.update("UPDATE trips SET departure_time=? WHERE id=?",com.busgo.common.time.JpaJdbcTime.parameter(now.plusHours(5)),f.trip().getId());
        jdbc.update("UPDATE transport_operators SET status='INACTIVE' WHERE id=?",f.operator().getId()); em.clear();
        assertThat(cancellations.customerRead(owner.user(),b.bookingId()).eligible()).isTrue();
        assertThat(cancellations.customerCancel(owner.user(),b.bookingId(),reason).reasonCode()).isEqualTo("CUSTOMER_CANCELLED");
    }

    @Test void customerPaidCancellationKeepsHistoricalVoidTicketsReadable() {
        var f=fixture(); var owner=customer("m17-paid-web"); authenticate(owner.user());
        var b=bookingService.create(owner.user(),new BookingDtos.CreateBookingRequest(hold(owner,f,0,2,0).holdToken(),"Web","0901234567","web@example.test"));
        paymentService.confirm(owner.user(),b.bookingId());
        cancellations.customerCancel(owner.user(),b.bookingId(),reason);
        em.clear();
        assertThat(paymentService.ticket(owner.user(),b.bookingId()).tickets()).allMatch(t->t.status().equals("VOID") && t.qrData()==null);
        assertThat(bookingService.detail(owner.user(),b.bookingId()).recovery().refunds()).hasSize(1);
    }

    @Test void paidSuspensionCutoffAndTerminalAttendanceRejectCancellation() {
        var f=fixture(); var owner=customer("m17-guard"); authenticate(owner.user());
        var b=bookingService.create(owner.user(),new BookingDtos.CreateBookingRequest(hold(owner,f,0,2,0).holdToken(),"Web","0901234567","web@example.test"));
        paymentService.confirm(owner.user(),b.bookingId());
        jdbc.update("UPDATE transport_operators SET status='INACTIVE' WHERE id=?",f.operator().getId()); em.clear();
        assertThat(cancellations.customerRead(owner.user(),b.bookingId()).ineligibleReason()).isEqualTo("CANCELLATION_OPERATOR_SUSPENDED");
        jdbc.update("UPDATE transport_operators SET status='ACTIVE' WHERE id=?",f.operator().getId());
        jdbc.update("UPDATE trip_stops SET planned_departure_time=? WHERE id=?",com.busgo.common.time.JpaJdbcTime.parameter(LocalDateTime.now(ZoneOffset.UTC).plusHours(5)),f.stops().get(0).getId()); em.clear();
        assertThat(cancellations.customerRead(owner.user(),b.bookingId()).ineligibleReason()).isEqualTo("CUSTOMER_CANCELLATION_CUTOFF");
        var admin=actor(f,RoleCode.OPERATOR_ADMIN); authenticate(admin);
        for(String state:List.of("CHECKED_IN","BOARDED","NO_SHOW")) {
            jdbc.update("UPDATE ticket_boarding a JOIN booking_items i ON i.id=a.booking_item_id SET a.status=? WHERE i.booking_id=?",state,b.bookingId());
            assertThatThrownBy(()->cancellations.operatorCancel(admin,b.bookingId(),reason)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getCode()).isEqualTo("CANCELLATION_ATTENDANCE_CONFLICT"));
        }
    }

    @Test void expiryHonorsDeadlinesLegacyPayOnBoardAndAttendance() {
        var f=fixture(); var admin=actor(f,RoleCode.OPERATOR_ADMIN); authenticate(admin);
        var b=assisted.create(admin,request(f,0,2,PaymentMethod.QR_TRANSFER));
        assertThat(cancellations.operatorRead(admin,b.bookingId()).paymentDueAt()).isNotNull();
        assertThat(cancellations.expire(b.bookingId())).isFalse();
        jdbc.update("UPDATE bookings SET payment_due_at=? WHERE id=?",com.busgo.common.time.JpaJdbcTime.parameter(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)),b.bookingId()); em.clear();
        assertThat(cancellations.expire(b.bookingId())).isTrue(); assertThat(cancellations.expire(b.bookingId())).isFalse();
        assertThat(cancellations.operatorRead(admin,b.bookingId()).reasonCode()).isEqualTo("PAYMENT_TIMEOUT");
        var payOnBoard=assisted.create(admin,request(f,0,2,PaymentMethod.PAY_ON_BOARD));
        assertThat(cancellations.operatorRead(admin,payOnBoard.bookingId()).paymentDueAt()).isNull();
        assertThat(cancellations.expire(payOnBoard.bookingId())).isFalse();
        var legacy=assisted.create(admin,request(f,2,3,PaymentMethod.QR_TRANSFER));
        jdbc.update("UPDATE bookings SET payment_due_at=NULL WHERE id=?",legacy.bookingId()); em.clear();
        assertThat(cancellations.expire(legacy.bookingId())).isFalse();
    }

    @Test void rolesOwnershipAnonymousAndPublicTokenCannotCancelForeignBookings() throws Exception {
        var f=fixture(); var admin=actor(f,RoleCode.OPERATOR_ADMIN); authenticate(admin);
        var b=assisted.create(admin,request(f,0,2,PaymentMethod.PAY_ON_BOARD));
        var foreign=actor(fixture(),RoleCode.OPERATOR_ADMIN); var customer=customer("m17-foreign");
        mvc.perform(post("/api/v1/operator/bookings/{id}/cancel",b.bookingId()).header("Authorization",bearer(foreign)).contentType("application/json").content("{}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/bookings/{id}/cancel",b.bookingId()).header("Authorization","Bearer "+customer.token()).contentType("application/json").content("{}")).andExpect(status().isNotFound());
        for(var denied:List.of(actor(f,RoleCode.OPERATOR_STAFF),actor(f,RoleCode.SYSTEM_ADMIN))) {
            mvc.perform(post("/api/v1/operator/bookings/{id}/cancel",b.bookingId()).header("Authorization",bearer(denied)).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        }
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        mvc.perform(post("/api/v1/bookings/{id}/cancel",b.bookingId()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/public/payments/fake/cancel").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
    }

    @Test void refundCannotHaveWrongAmountOrUnpaidPayment() {
        var f=fixture(); var admin=actor(f,RoleCode.OPERATOR_ADMIN); authenticate(admin);
        var b=assisted.create(admin,request(f,0,2,PaymentMethod.PAY_ON_BOARD));
        var paid=assisted.record(admin,b.bookingId(),new RecordPayment(PaymentMethod.PAY_ON_BOARD,null));
        assertThatThrownBy(()->jdbc.update("INSERT INTO refunds(payment_id,amount,payment_paid_at,refunded_at,reason_code,created_at) SELECT id,1,paid_at,UTC_TIMESTAMP(6),'OPERATOR_CANCELLED',UTC_TIMESTAMP(6) FROM payments WHERE id=?",paid.paymentId())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update("UPDATE payments SET status='PENDING',paid_at=NULL WHERE id=?",paid.paymentId());
        assertThatThrownBy(()->jdbc.update("INSERT INTO refunds(payment_id,amount,payment_paid_at,refunded_at,reason_code,created_at) SELECT id,amount,paid_at,UTC_TIMESTAMP(6),'OPERATOR_CANCELLED',UTC_TIMESTAMP(6) FROM payments WHERE id=?",paid.paymentId())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
