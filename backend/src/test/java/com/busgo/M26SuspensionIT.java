package com.busgo;

import static org.assertj.core.api.Assertions.*;
import com.busgo.onboarding.OnboardingDtos.*;
import com.busgo.booking.*;
import com.busgo.operator.entity.OperatorStatus;
import com.busgo.user.entity.RoleCode;
import com.busgo.trip.entity.TripStatus;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties="busgo.notifications.initial-delay=PT24H") @ActiveProfiles("dev") @Transactional
class M26SuspensionIT extends M26Support {
    @Autowired com.busgo.marketplace.MarketplaceService marketplace;
    @Autowired com.busgo.booking.CancellationService cancellations;
    @Autowired com.busgo.booking.PartialCancellationService partial;
    @Autowired com.busgo.trip.operations.LiveTripOperationsService live;
    @Autowired com.busgo.trip.operations.OperatorTripOperationsService lifecycle;
    @Autowired com.busgo.trip.TripService supply;
    void suspend(Fixture f) { onboarding.status(system(),f.operator().getId(),new Reason("Platform review"),true);em.clear(); }
    @Test void suspensionPreservesBookingTicketPaymentAndInventory() {
        var f=fixture();var owner=customer("m26-preserve");var b=web(f,owner,0);paymentService.confirm(owner.user(),b.bookingId());
        var before=jdbc.queryForList("SELECT id,status,booking_item_id FROM trip_seat_segment_inventory WHERE trip_seat_id IN (SELECT id FROM trip_seats WHERE trip_id=?)",f.trip().getId());
        suspend(f);assertThat(bookingService.detail(owner.user(),b.bookingId()).bookingId()).isEqualTo(b.bookingId());
        assertThat(paymentService.ticket(owner.user(),b.bookingId()).tickets()).hasSize(1);
        assertThat(jdbc.queryForList("SELECT id,status,booking_item_id FROM trip_seat_segment_inventory WHERE trip_seat_id IN (SELECT id FROM trip_seats WHERE trip_id=?)",f.trip().getId())).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refunds r JOIN payments p ON p.id=r.payment_id WHERE p.booking_id=?",Long.class,b.bookingId())).isZero();
    }
    @Test void suspendedOperatorHiddenAndNewHoldBookingAssistedDenied() {
        var f=fixture();var owner=customer("m26-new");var held=hold(owner,f,0,2,0);var admin=actor(f,RoleCode.OPERATOR_ADMIN);suspend(f);authenticate(admin);
        assertThatThrownBy(()->marketplace.profile(f.operator().getId())).isInstanceOf(com.busgo.common.exception.BusinessException.class);
        assertThatThrownBy(()->hold(owner,f,0,2,1)).isInstanceOf(com.busgo.common.exception.BusinessException.class);
        assertThatThrownBy(()->bookingService.create(owner.user(),new BookingDtos.CreateBookingRequest(held.holdToken(),"User","0901234567","u@example.test"))).isInstanceOf(com.busgo.common.exception.BusinessException.class);
        assertThatThrownBy(()->assisted.create(admin,request(f,0,2,com.busgo.payment.entity.PaymentMethod.PAY_ON_BOARD))).isInstanceOf(com.busgo.common.exception.BusinessException.class);
    }
    @Test void paidCustomerCanCancelAndRefundUnderSuspension() {
        var f=fixture();var owner=customer("m26-cancel");var b=web(f,owner,0);paymentService.confirm(owner.user(),b.bookingId());suspend(f);
        authenticate(owner.user());cancellations.customerCancel(owner.user(),b.bookingId(),new CancellationDtos.Request(null));
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,b.bookingId())).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refunds r JOIN payments p ON p.id=r.payment_id WHERE p.booking_id=?",Long.class,b.bookingId())).isOne();
    }
    @Test void partialCancellationKeepsExistingRulesUnderSuspension() {
        var f=expanded();var owner=customer("m26-partial");var b=web(f,owner,0,1);paymentService.confirm(owner.user(),b.bookingId());suspend(f);
        partial.execute(owner.user(),b.bookingId(),false,new PartialCancellationDtos.Request(List.of(itemIds(b.bookingId()).get(0))));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking_items WHERE booking_id=? AND cancelled=TRUE",Long.class,b.bookingId())).isOne();
        assertThat(modifications.eligibility(owner.user(),b.bookingId(),false).seatChange().allowed()).isFalse();
    }
    @ParameterizedTest @EnumSource(value=TripStatus.class,names={"SCHEDULED","BOARDING","DEPARTED"})
    void scheduledBoardingAndDepartedTripsRemainServiceable(TripStatus state) {
        var f=fixture();var owner=customer("m26-trip");var b=web(f,owner,0);paymentService.confirm(owner.user(),b.bookingId());
        var admin=actor(f,RoleCode.OPERATOR_ADMIN);f.trip().setStatus(state);trips.flush();suspend(f);authenticate(admin);
        assertThat(lifecycle.manifest(admin,f.trip().getId()).tripId()).isEqualTo(f.trip().getId());
        var r=live.update(admin,f.trip().getId(),new com.busgo.trip.operations.LiveTripOperationsService.Update("suspended-delay",10,"Traffic",null),false);
        assertThat(r.delayMinutes()).isEqualTo(10);
    }
    @Test void suspendedDepartedTripCanComplete() {
        var f=fixture();var admin=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(admin);
        M16BFixtures.crew(jdbc,f.operator().getId(),f.trip().getId(),admin.id());
        f.trip().setStatus(TripStatus.DEPARTED);trips.flush();
        for(var stop:f.stops()) if(stop.isAllowPickup()) jdbc.update("INSERT INTO trip_stop_operations(trip_id,stop_id,pickup_closed_at,pickup_closed_by) VALUES(?,?,UTC_TIMESTAMP(),?)",f.trip().getId(),stop.getId(),admin.id());
        suspend(f);lifecycle.updateStatus(admin,f.trip().getId(),TripStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT status FROM trips WHERE id=?",String.class,f.trip().getId())).isEqualTo("COMPLETED");
    }
    @Test void suspendedCannotCreateSupplyAndReactivationRestoresProfile() {
        var f=fixture();var admin=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(admin);suspend(f);
        assertThatThrownBy(()->supply.create(admin,new com.busgo.trip.TripDtos.CreateTripRequest(f.operatorRoute().getId(),f.bus().getId(),f.trip().getDepartureTime().plusDays(1).atOffset(java.time.ZoneOffset.UTC)))).isInstanceOf(com.busgo.common.exception.BusinessException.class);
        jdbc.update("UPDATE transport_operators SET onboarded_at=created_at WHERE id=?",f.operator().getId());
        onboarding.status(system(),f.operator().getId(),new Reason("Resolved"),false);em.clear();
        assertThat(marketplace.profile(f.operator().getId()).operator().id()).isEqualTo(f.operator().getId());
    }
}
