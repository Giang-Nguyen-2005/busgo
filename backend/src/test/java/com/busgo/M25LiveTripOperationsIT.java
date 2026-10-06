package com.busgo;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.busgo.booking.*;
import com.busgo.common.security.CurrentUser;
import com.busgo.marketplace.MarketplaceService;
import com.busgo.trip.entity.*;
import com.busgo.trip.operations.*;
import com.busgo.trip.search.*;
import com.busgo.user.entity.RoleCode;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties="busgo.notifications.initial-delay=PT24H")
@AutoConfigureMockMvc @ActiveProfiles("dev") @Transactional
class M25LiveTripOperationsIT extends M20Support {
    @Autowired LiveTripOperationsService live;
    @Autowired OperatorTripOperationsService lifecycle;
    @Autowired PartialCancellationService partial;
    @Autowired CancellationService cancellations;
    @Autowired MarketplaceService marketplace;
    @Autowired TripSearchService search;
    @Autowired MockMvc mvc;
    @AfterEach void clear() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    CurrentUser admin(Fixture f) { var a=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(a);return a; }
    LiveTripOperationsService.Update request(String key,int delay) { return new LiveTripOperationsService.Update(key,delay,"Ùn tắc giao thông",null); }
    long events(long booking,String type) { return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE booking_id=? AND event_type=?",Long.class,booking,type); }
    void closePickups(Fixture f,CurrentUser a) {
        jdbc.update("UPDATE ticket_boarding a JOIN booking_items i ON i.id=a.booking_item_id JOIN bookings b ON b.id=i.booking_id SET a.status='BOARDED' WHERE b.trip_id=?",f.trip().getId());
        for(var s:f.stops()) if(s.isAllowPickup()) jdbc.update("INSERT INTO trip_stop_operations(trip_id,stop_id,pickup_closed_at,pickup_closed_by) VALUES(?,?,UTC_TIMESTAMP(6),?)",f.trip().getId(),s.getId(),a.id());
    }
    @Test void publishUpdateClearKeepTimetableAndImmutableSnapshots() {
        var f=fixture();var a=admin(f);var planned=f.trip().getDepartureTime();
        var s=live.update(a,f.trip().getId(),request("first",20),false);
        assertThat(s.expectedDepartureAt().toLocalDateTime()).isEqualTo(planned.plusMinutes(20));
        assertThat(s.expectedArrivalAt().toLocalDateTime()).isEqualTo(f.trip().getEstimatedArrivalTime().plusMinutes(20));
        live.update(a,f.trip().getId(),request("second",30),false);
        live.update(a,f.trip().getId(),new LiveTripOperationsService.Update("clear",0,null,null),false);
        assertThat(live.current(a,f.trip().getId()).expectedDepartureAt().toLocalDateTime()).isEqualTo(planned);
        assertThat(f.trip().getDepartureTime()).isEqualTo(planned);
        var history=live.history(a,f.trip().getId());assertThat(history).hasSize(3);
        assertThat(history.get(2).state().delayMinutes()).isEqualTo(20);
        assertThat(history.get(0).updateType()).isEqualTo("DELAY_CLEARED");
    }
    @Test void negativeDelayRejected() { var f=fixture();var a=admin(f);assertThatThrownBy(()->live.update(a,f.trip().getId(),request("negative",-1),false)).hasMessageContaining("Invalid request"); }
    @ParameterizedTest @EnumSource(value=TripStatus.class,names={"COMPLETED","CANCELLED"})
    void terminalRejectsUpdate(TripStatus status) { var f=fixture();var a=admin(f);f.trip().setStatus(status);trips.flush();assertThatThrownBy(()->live.update(a,f.trip().getId(),request("terminal",5),false)).hasMessageContaining("Terminal"); }
    @Test void intermediatePickupReadDtoUsesBookedStop() {
        var f=fixture();var owner=customer("m25-pickup");var held=hold(owner,f,1,3,0);
        var b=bookingService.create(owner.user(),new BookingDtos.CreateBookingRequest(held.holdToken(),"Test","0900000000","test@example.test"));
        var a=admin(f);live.update(a,f.trip().getId(),request("pickup",25),false);
        var response=bookingService.detail(owner.user(),b.bookingId());
        assertThat(response.operations().selectedPickupScheduledAt()).isEqualTo(response.pickup().time());
        assertThat(response.operations().selectedPickupEstimatedAt()).isEqualTo(response.pickup().time().plusMinutes(25));
        assertThat(response.operations().label()).isEqualTo("Trễ 25 phút");
    }
    @Test void retryDeduplicatesAndLaterPublicationNotifiesAgain() {
        var f=fixture();var owner=customer("m25-retry");var b=web(f,owner,0);paymentService.confirm(owner.user(),b.bookingId());var a=admin(f);
        live.update(a,f.trip().getId(),request("same",20),false);live.update(a,f.trip().getId(),request("same",20),false);
        assertThat(events(b.bookingId(),"TRIP_DELAYED")).isOne();assertThat(live.history(a,f.trip().getId())).hasSize(1);
        live.update(a,f.trip().getId(),request("new",30),false);
        assertThat(events(b.bookingId(),"TRIP_DELAYED")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT message FROM notifications WHERE booking_id=? AND event_type='TRIP_DELAYED' ORDER BY id DESC LIMIT 1",String.class,b.bookingId())).contains(b.bookingCode(),"Trễ 30 phút","Ùn tắc");
    }
    @Test void keyReuseWithDifferentPayloadConflicts() { var f=fixture();var a=admin(f);live.update(a,f.trip().getId(),request("same",20),false);assertThatThrownBy(()->live.update(a,f.trip().getId(),request("same",30),false)).hasMessageContaining("different content"); }
    @Test void departedAndCompletedRecordOnceNotifyAndUnlockReview() {
        var f=fixture();var owner=customer("m25-lifecycle");var b=web(f,owner,0);paymentService.confirm(owner.user(),b.bookingId());var a=admin(f);
        M16BFixtures.crew(jdbc,f.operator().getId(),f.trip().getId(),a.id());
        lifecycle.updateStatus(a,f.trip().getId(),TripStatus.BOARDING);closePickups(f,a);
        authenticate(owner.user());assertThat(marketplace.eligibility(owner.user(),b.bookingId()).eligible()).isFalse();authenticate(a);
        lifecycle.updateStatus(a,f.trip().getId(),TripStatus.DEPARTED);var departed=f.trip().getActualDepartureAt();
        lifecycle.updateStatus(a,f.trip().getId(),TripStatus.DEPARTED);assertThat(f.trip().getActualDepartureAt()).isEqualTo(departed);
        assertThat(events(b.bookingId(),"TRIP_DEPARTED")).isOne();
        lifecycle.updateStatus(a,f.trip().getId(),TripStatus.COMPLETED);var arrived=f.trip().getActualArrivalAt();
        lifecycle.updateStatus(a,f.trip().getId(),TripStatus.COMPLETED);assertThat(f.trip().getActualArrivalAt()).isEqualTo(arrived).isAfterOrEqualTo(departed);
        assertThat(events(b.bookingId(),"TRIP_COMPLETED")).isOne();assertThat(live.history(a,f.trip().getId())).hasSize(2);
        authenticate(owner.user());assertThat(marketplace.eligibility(owner.user(),b.bookingId()).eligible()).isTrue();
    }
    @Test void departedEtaMustFollowActualDepartureAndCannotRegressLifecycle() {
        var f=fixture();var a=admin(f);f.trip().setStatus(TripStatus.DEPARTED);f.trip().setActualDepartureAt(f.trip().getDepartureTime());trips.flush();
        assertThatThrownBy(()->live.update(a,f.trip().getId(),new LiveTripOperationsService.Update("bad",20,null,f.trip().getDepartureTime().atOffset(ZoneOffset.UTC)),true)).hasMessageContaining("arrival must follow");
    }
    @Test void etaUpdatesCurrentArrivalWithoutChangingActualOrSchedule() {
        var f=fixture();var a=admin(f);f.trip().setStatus(TripStatus.DEPARTED);f.trip().setActualDepartureAt(f.trip().getDepartureTime());trips.flush();
        var estimate=f.trip().getEstimatedArrivalTime().plusMinutes(15).atOffset(ZoneOffset.UTC);
        assertThat(live.update(a,f.trip().getId(),new LiveTripOperationsService.Update("eta",15,null,estimate),true).expectedArrivalAt()).isEqualTo(estimate);
        assertThat(live.history(a,f.trip().getId()).get(0).updateType()).isEqualTo("ETA_UPDATED");
        assertThatThrownBy(()->lifecycle.updateStatus(a,f.trip().getId(),TripStatus.BOARDING)).hasMessageContaining("cannot transition");
    }
    @Test void foreignOperatorConcealed() { var f=fixture();var a=admin(fixture());assertThatThrownBy(()->live.update(a,f.trip().getId(),request("foreign",10),false)).isInstanceOf(com.busgo.common.exception.ResourceNotFoundException.class); }
    @ParameterizedTest @EnumSource(value=RoleCode.class,names={"OPERATOR_STAFF","SYSTEM_ADMIN"})
    void staffAndSystemAdminCannotWrite(RoleCode role) throws Exception {
        var f=fixture();var a=actor(f,role);authenticate(a);
        mvc.perform(post("/api/v1/operator/trips/"+f.trip().getId()+"/delay").header("Authorization",bearer(a)).contentType("application/json").content("{\"requestKey\":\"denied\",\"delayMinutes\":20}")).andExpect(status().isForbidden());
    }
    @Test void staffReadsButHistoryHasNoMutationApi() throws Exception {
        var f=fixture();var a=actor(f,RoleCode.OPERATOR_STAFF);authenticate(a);String path="/api/v1/operator/trips/"+f.trip().getId();
        mvc.perform(get(path+"/operations").header("Authorization",bearer(a))).andExpect(status().isOk());
        mvc.perform(get(path+"/operational-history").header("Authorization",bearer(a))).andExpect(status().isOk());
        authenticate(a);assertThat(live.current(a,f.trip().getId()).label()).isEqualTo("Đúng giờ");
    }
    @Test void m20OnlyCurrentTripReceivesUpdates() {
        var f=expanded();var owner=customer("m25-m20");authenticate(owner.user());var b=web(f,owner,0);paymentService.confirm(owner.user(),b.bookingId());var target=targetTrip(f);
        var m=modifications.create(owner.user(),b.bookingId(),false,tripRequest(b.bookingId(),target));modifications.confirm(owner.user(),b.bookingId(),false,m.id());
        var a=admin(f);live.update(a,f.trip().getId(),request("old",20),false);assertThat(events(b.bookingId(),"TRIP_DELAYED")).isZero();
        live.update(a,target.getId(),request("current",30),false);assertThat(events(b.bookingId(),"TRIP_DELAYED")).isOne();
        assertThat(bookingService.detail(owner.user(),b.bookingId()).operations().delayMinutes()).isEqualTo(30);
    }
    @Test void m21PartialReceivesAndCancelledItemsExcludedFromMessage() {
        var f=expanded();var owner=customer("m25-m21");authenticate(owner.user());var b=web(f,owner,0,1);paymentService.confirm(owner.user(),b.bookingId());partial.execute(owner.user(),b.bookingId(),false,new PartialCancellationDtos.Request(List.of(itemIds(b.bookingId()).get(0))));
        var a=admin(f);live.update(a,f.trip().getId(),request("partial",20),false);
        assertThat(events(b.bookingId(),"TRIP_DELAYED")).isOne();
        assertThat(jdbc.queryForObject("SELECT message FROM notifications WHERE booking_id=? AND event_type='TRIP_DELAYED'",String.class,b.bookingId())).contains("A02").doesNotContain("A01");
    }
    @Test void fullyCancelledBookingExcluded() {
        var f=fixture();var owner=customer("m25-cancelled");authenticate(owner.user());var b=web(f,owner,0);paymentService.confirm(owner.user(),b.bookingId());cancellations.customerCancel(owner.user(),b.bookingId(),new CancellationDtos.Request(null));
        var a=admin(f);live.update(a,f.trip().getId(),request("cancelled",20),false);assertThat(events(b.bookingId(),"TRIP_DELAYED")).isZero();
    }
    @Test void bookingWithoutActiveItemsExcluded() {
        var f=fixture();var owner=customer("m25-empty");var b=web(f,owner,0);paymentService.confirm(owner.user(),b.bookingId());jdbc.update("UPDATE booking_items SET cancelled=TRUE WHERE booking_id=?",b.bookingId());em.clear();
        var a=admin(f);live.update(a,f.trip().getId(),request("empty",20),false);assertThat(events(b.bookingId(),"TRIP_DELAYED")).isZero();
    }
    @Test void searchKeepsScheduledDateAndSelectedJourneyTimes() {
        var f=fixture();var a=admin(f);live.update(a,f.trip().getId(),request("search",1500),false);
        var rows=search.search(f.locations().get(1).getId(),f.locations().get(3).getId(),LocalDate.of(2030,9,20),null,null,null,null,null,null,TripSearchDtos.SearchSort.DEPARTURE_ASC,null,1,0,10).data();
        assertThat(rows).hasSize(1);assertThat(rows.get(0).pickup().departureTime().toLocalDateTime()).isEqualTo(f.stops().get(1).getPlannedDepartureTime());
        assertThat(rows.get(0).expectedPickupAt()).isEqualTo(rows.get(0).pickup().departureTime().plusMinutes(1500));
    }
    @Test void safeReasonAndExplicitEstimateValidation() {
        var f=fixture();var a=admin(f);assertThatThrownBy(()->live.update(a,f.trip().getId(),new LiveTripOperationsService.Update("unsafe",0,"Bad\u0000text",null),false)).hasMessageContaining("reason");
    }
    @Test void completionRejectsBackendTimeBeforeRecordedDeparture() {
        var f=fixture();var a=admin(f);f.trip().setStatus(TripStatus.DEPARTED);f.trip().setActualDepartureAt(LocalDateTime.of(2099,1,1,0,0));trips.flush();closePickups(f,a);
        assertThatThrownBy(()->lifecycle.updateStatus(a,f.trip().getId(),TripStatus.COMPLETED)).hasMessageContaining("Arrival cannot precede departure");
    }
}
