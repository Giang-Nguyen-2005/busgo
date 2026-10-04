package com.busgo;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.busgo.customer.*;
import com.busgo.customer.CustomerDtos.*;
import com.busgo.booking.*;
import com.busgo.booking.AssistedBookingDtos.*;
import com.busgo.payment.PaymentTicketService;
import com.busgo.payment.entity.PaymentMethod;
import com.busgo.user.entity.RoleCode;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev") @Transactional
class M18BCustomersIT extends M16ATestSupport {
    @Autowired CustomerService customers;
    @Autowired CancellationService cancellation;
    @Autowired PaymentTicketService payment;
    @Autowired MockMvc mvc;
    private CustomerFilter filter() { return new CustomerFilter(null,null,null,0,20); }
    @AfterEach void clear() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    @Test void repeatedOfflinePhonesRemainSeparateAndForeignContactsAreNotFound() {
        var f=fixture();var foreign=fixture();var admin=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(admin);
        long usersBefore=users.count();
        var a=assisted.create(admin,request(f,0,1,PaymentMethod.PAY_ON_BOARD));
        var b=assisted.create(admin,request(f,1,2,PaymentMethod.PAY_ON_BOARD));
        var other=actor(foreign,RoleCode.OPERATOR_ADMIN);authenticate(other);
        var c=assisted.create(other,request(foreign,0,1,PaymentMethod.PAY_ON_BOARD));authenticate(admin);
        var rows=customers.directory(admin,filter());assertThat(rows.data()).hasSize(2);
        assertThat(rows.data()).allSatisfy(r->{assertThat(r.customerType()).isEqualTo(Type.OFFLINE_CONTACT);assertThat(r.totalBookings()).isOne();});
        assertThat(rows.data()).extracting(Summary::customerKey).containsExactlyInAnyOrder("CONTACT:"+a.bookingId(),"CONTACT:"+b.bookingId());
        assertThat(users.count()).isEqualTo(usersBefore+1); // Only the explicitly created foreign admin.
        assertThatThrownBy(()->customers.detail(admin,"CONTACT:"+c.bookingId(),0,20)).hasMessageContaining("not found");
    }
    @Test void sameAccountAcrossOperatorsGroupsOnlyOwnedBookingsAndUsesLocalSnapshots() {
        var f=fixture();var foreign=fixture();var user=customer("Global profile");authenticate(user.user());
        var a=bookingService.create(user.user(),new BookingDtos.CreateBookingRequest(hold(user,f,0,1,0).holdToken(),"Earlier local","0901111111","old@example.test"));
        var b=bookingService.create(user.user(),new BookingDtos.CreateBookingRequest(hold(user,f,1,2,0).holdToken(),"Latest local","0902222222","latest@example.test"));
        bookingService.create(user.user(),new BookingDtos.CreateBookingRequest(hold(user,foreign,0,1,0).holdToken(),"Foreign secret","0903333333","foreign@example.test"));
        var admin=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(admin);
        String key="ACCOUNT:"+user.user().id();var detail=customers.detail(admin,key,0,20);
        assertThat(detail.summary().displayName()).isEqualTo("Latest local");assertThat(detail.summary().totalBookings()).isEqualTo(2);
        assertThat(detail.summary().email()).isEqualTo("latest@example.test");
        assertThat(detail.bookings().data()).extracting(BookingHistory::bookingId).containsExactly(b.bookingId(),a.bookingId());
        assertThat(customers.directory(admin,new CustomerFilter("old@example.test",null,null,0,20)).data()).hasSize(1);
        assertThat(customers.directory(admin,new CustomerFilter("Foreign secret",null,null,0,20)).data()).isEmpty();
        var other=actor(foreign,RoleCode.OPERATOR_ADMIN);authenticate(other);
        assertThat(customers.detail(other,key,0,20).summary().totalBookings()).isOne();
    }
    @Test void multiSeatRefundKeepsGrossAndCountsDedicatedRefundOnce() {
        var f=fixture();var admin=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(admin);
        var b=assisted.create(admin,new CreateRequest(f.trip().getId(),f.locations().get(0).getId(),f.locations().get(1).getId(),f.seats().stream().map(s->s.getId()).toList(),"Money","0901234567",null,PaymentMethod.PAY_ON_BOARD));
        assisted.record(admin,b.bookingId(),new RecordPayment(PaymentMethod.PAY_ON_BOARD,null));
        cancellation.operatorCancel(admin,b.bookingId(),new CancellationDtos.Request(null));
        var d=customers.detail(admin,"CONTACT:"+b.bookingId(),0,20);var m=d.summary().money();
        assertThat(m.grossMockPaid()).isEqualByComparingTo("200");assertThat(m.mockRefunds()).isEqualByComparingTo("200");assertThat(m.netMockPaid()).isZero();
        var h=d.bookings().data().get(0);assertThat(h.voidTickets()).isEqualTo(2);assertThat(h.payments()).hasSize(1);assertThat(h.refunds()).hasSize(1);
        assertThat(h.payments().get(0).status()).isEqualTo("REFUNDED");assertThat(h.cancellationReason()).isEqualTo("OPERATOR_CANCELLED");
        var unpaid=assisted.create(admin,request(f,0,1,PaymentMethod.QR_TRANSFER));cancellation.operatorCancel(admin,unpaid.bookingId(),new CancellationDtos.Request(null));
        assertThat(customers.detail(admin,"CONTACT:"+unpaid.bookingId(),0,20).summary().money().grossMockPaid()).isZero();
    }
    @Test void mixedAttendanceAndTicketlessNoShowAreTruthful() {
        var f=fixture();var admin=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(admin);
        var b=assisted.create(admin,new CreateRequest(f.trip().getId(),f.locations().get(0).getId(),f.locations().get(1).getId(),f.seats().stream().map(s->s.getId()).toList(),"Mixed","0901234567",null,PaymentMethod.PAY_ON_BOARD));
        assisted.record(admin,b.bookingId(),new RecordPayment(PaymentMethod.PAY_ON_BOARD,null));
        var ids=jdbc.queryForList("SELECT id FROM tickets WHERE booking_id=? ORDER BY id",Long.class,b.bookingId());
        jdbc.update("UPDATE ticket_boarding SET status='BOARDED' WHERE ticket_id=?",ids.get(0));jdbc.update("UPDATE ticket_boarding SET status='NO_SHOW' WHERE ticket_id=?",ids.get(1));
        var d=customers.detail(admin,"CONTACT:"+b.bookingId(),0,20);assertThat(d.summary().boardedJourneys()).isOne();assertThat(d.summary().attendance()).isEqualTo(new Attendance(1,1,0,0));
        var unpaid=assisted.create(admin,request(f,1,2,PaymentMethod.PAY_ON_BOARD));
        jdbc.update("INSERT INTO ticket_boarding(booking_item_id,status,pickup_stop_id) SELECT id,'NO_SHOW',? FROM booking_items WHERE booking_id=?",f.stops().get(1).getId(),unpaid.bookingId());
        d=customers.detail(admin,"CONTACT:"+unpaid.bookingId(),0,20);assertThat(d.summary().attendance().noShow()).isOne();assertThat(d.bookings().data().get(0).validTickets()).isZero();assertThat(d.summary().boardedJourneys()).isZero();
        jdbc.update("UPDATE ticket_boarding SET status='CHECKED_IN' WHERE ticket_id=?",ids.get(0));jdbc.update("DELETE FROM ticket_boarding WHERE ticket_id=?",ids.get(1));
        d=customers.detail(admin,"CONTACT:"+b.bookingId(),0,20);assertThat(d.summary().attendance()).isEqualTo(new Attendance(0,0,1,1));
    }
    @Test void literalSearchStablePagingSortsAndTypeFilters() {
        var f=fixture();var admin=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(admin);
        var a=assisted.create(admin,request(f,0,1,PaymentMethod.PAY_ON_BOARD));var b=assisted.create(admin,request(f,1,2,PaymentMethod.PAY_ON_BOARD));
        jdbc.update("UPDATE bookings SET created_at=(SELECT x.created_at FROM (SELECT created_at FROM bookings WHERE id=?) x) WHERE id=?",a.bookingId(),b.bookingId());
        for(var sort:Sort.values()) {
            var first=customers.directory(admin,new CustomerFilter("CALLER",Type.OFFLINE_CONTACT,sort,0,1));var next=customers.directory(admin,new CustomerFilter("CALLER",Type.OFFLINE_CONTACT,sort,1,1));
            assertThat(first.pagination().totalElements()).isEqualTo(2);assertThat(first.data().get(0).customerKey()).isNotEqualTo(next.data().get(0).customerKey());
        }
        assertThat(customers.directory(admin,new CustomerFilter("%",null,null,0,20)).data()).isEmpty();
        assertThat(customers.directory(admin,new CustomerFilter("0901234567",null,null,0,20)).data()).hasSize(2);
        assertThat(customers.directory(admin,new CustomerFilter(null,Type.ACCOUNT,null,0,20)).data()).isEmpty();
    }
    @Test void bothEndpointsRejectUnauthorizedAndReturnOnlyTypedSafeFields() throws Exception {
        var f=fixture();var admin=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(admin);var b=assisted.create(admin,request(f,0,1,PaymentMethod.PAY_ON_BOARD));
        var mixed=actor(f,RoleCode.OPERATOR_ADMIN);
        userRoles.saveAndFlush(new com.busgo.user.entity.UserRole(users.findById(mixed.id()).orElseThrow(),roles.findByCode(RoleCode.SYSTEM_ADMIN).orElseThrow()));
        for(var path:List.of("/api/v1/operator/customers","/api/v1/operator/customers/CONTACT:"+b.bookingId())) {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();mvc.perform(get(path)).andExpect(status().isUnauthorized());
            for(var role:List.of(RoleCode.OPERATOR_STAFF,RoleCode.CUSTOMER,RoleCode.SYSTEM_ADMIN)) { var denied=actor(f,role);org.springframework.security.core.context.SecurityContextHolder.clearContext();mvc.perform(get(path).header("Authorization",bearer(denied))).andExpect(status().isForbidden()); }
            mvc.perform(get(path).header("Authorization",bearer(mixed))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/operator/customers").header("Authorization",bearer(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.data.data[0].customerType").value("OFFLINE_CONTACT")).andExpect(jsonPath("$.data.data[0].roles").doesNotExist()).andExpect(jsonPath("$.data.data[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/v1/operator/customers/CONTACT:"+b.bookingId()).header("Authorization",bearer(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.data.bookings.data[0].createdAt").value(org.hamcrest.Matchers.endsWith("Z")));
        mvc.perform(get("/api/v1/operator/customers?sort=SQL&page=0").header("Authorization",bearer(admin))).andExpect(status().isBadRequest());
    }
}
