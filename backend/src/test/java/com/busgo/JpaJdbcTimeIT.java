package com.busgo;

import static org.assertj.core.api.Assertions.assertThat;

import com.busgo.admin.AdminOperatorQueryRepository;
import com.busgo.booking.BookingDtos.CreateBookingRequest;
import com.busgo.booking.BookingService;
import com.busgo.booking.repository.BookingRepository;
import com.busgo.booking.repository.OperatorBookingQueryRepository;
import com.busgo.operator.OperatorStaffQueryRepository;
import com.busgo.operator.entity.OperatorStaff;
import com.busgo.operator.repository.OperatorStaffRepository;
import com.busgo.payment.PaymentTicketService;
import com.busgo.trip.operations.OperatorOccupancyQueryRepository;
import com.busgo.common.entity.ActiveStatus;
import com.busgo.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.ZoneOffset;
import java.util.TimeZone;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @ActiveProfiles("dev") @Transactional
class JpaJdbcTimeIT extends M8BookingTestSupport {
    @Autowired BookingService bookings;
    @Autowired BookingRepository bookingRepository;
    @Autowired PaymentTicketService payments;
    @Autowired OperatorBookingQueryRepository bookingQueries;
    @Autowired OperatorOccupancyQueryRepository operationQueries;
    @Autowired AdminOperatorQueryRepository adminQueries;
    @Autowired OperatorStaffQueryRepository staffQueries;
    @Autowired OperatorStaffRepository staff;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;

    @ParameterizedTest @ValueSource(strings={"UTC", "Asia/Ho_Chi_Minh", "America/New_York"})
    void jpaAndJdbcViewsAgreeWithoutRewritingDataInEachJvmTimezone(String timezone) {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(timezone));
            var fixture = fixture();
            var owner = customer("M15 time");
            var held = hold(owner, fixture, 0, 3, 0);
            var booking = bookings.create(owner.user(), new CreateBookingRequest(
                    held.holdToken(), "Customer", "0900000015", "time@example.test"));
            var payment = payments.confirm(owner.user(), booking.bookingId());
            var stillHeld = hold(owner, fixture, 0, 3, 1);
            OperatorStaff membership = new OperatorStaff();
            membership.setOperator(fixture.operator());
            membership.setUser(users.findById(owner.user().id()).orElseThrow());
            membership.setStaffCode("TIME-CHECK"); membership.setStatus(ActiveStatus.ACTIVE);
            staff.saveAndFlush(membership);
            long membershipId = membership.getId();
            long operatorId = fixture.operator().getId();
            entityManager.flush(); entityManager.clear();

            var stored = bookingRepository.findById(booking.bookingId()).orElseThrow();
            var detail = bookingQueries.findOwnedDetail(operatorId, booking.bookingId()).orElseThrow();
            var manifest = operationQueries.manifest(operatorId, fixture.trip().getId());
            assertThat(detail.departureTime()).isEqualTo(stored.getTrip().getDepartureTime());
            assertThat(detail.estimatedArrivalTime()).isEqualTo(stored.getTrip().getEstimatedArrivalTime());
            assertThat(detail.createdAt()).isEqualTo(stored.getCreatedAt());
            assertThat(detail.updatedAt()).isEqualTo(stored.getUpdatedAt());
            assertThat(manifest.get(0).pickupTime()).isEqualTo(stored.getPickupTripStop().getPlannedDepartureTime());
            assertThat(manifest.get(0).dropoffTime()).isEqualTo(stored.getDropoffTripStop().getPlannedArrivalTime());
            var listed = bookingQueries.search(operatorId, null, fixture.trip().getId(), null, null,
                    stored.getCreatedAt().minusSeconds(1), stored.getCreatedAt().plusSeconds(1), 0, 20);
            assertThat(listed.total()).isEqualTo(1);
            assertThat(listed.rows().get(0).createdAt()).isEqualTo(stored.getCreatedAt());
            assertThat(bookingQueries.search(operatorId, null, fixture.trip().getId(), null, null,
                    stored.getCreatedAt().plusSeconds(1), stored.getCreatedAt().plusSeconds(2), 0, 20).total()).isZero();
            assertThat(adminQueries.detail(operatorId).orElseThrow().createdAt().toLocalDateTime())
                    .isEqualTo(operators.findById(operatorId).orElseThrow().getCreatedAt());
            assertThat(staffQueries.search(operatorId, null, null, null, 0, 20).rows().get(0).createdAt().toLocalDateTime())
                    .isEqualTo(staff.findById(membershipId).orElseThrow().getCreatedAt());
            assertThat(operationQueries.occupancy(operatorId, fixture.trip().getId()).stream()
                    .filter(row -> row.holdExpiresAt() != null).map(row -> row.holdExpiresAt()).distinct())
                    .containsExactly(stillHeld.expiresAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());
            assertThat(bookingQueries.findPayments(operatorId, booking.bookingId()).get(0).paidAt())
                    .isEqualTo(payment.paidAt().toLocalDateTime());
        } finally { TimeZone.setDefault(previous); }
    }
}
