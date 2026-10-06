package com.busgo;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.busgo.common.time.JpaJdbcTime.parameter;
import com.busgo.booking.*;
import com.busgo.booking.entity.Booking;
import com.busgo.notification.*;
import com.busgo.notification.NotificationDtos.Preferences;
import com.busgo.payment.entity.PaymentMethod;
import com.busgo.user.entity.RoleCode;
import java.time.*;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties="busgo.notifications.initial-delay=PT24H")
@AutoConfigureMockMvc @ActiveProfiles("dev") @Transactional
class M23NotificationsIT extends M20Support {
    @Autowired NotificationService notifications;
    @Autowired NotificationDeliveryWorker worker;
    @Autowired TripReminderJob reminders;
    @Autowired PartialCancellationService partial;
    @Autowired CancellationService cancellations;
    @Autowired MockMvc mvc;
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;
    @MockitoBean NotificationMailSender mail;
    private final List<Fixture> ownedFixtures=new ArrayList<>();
    private final List<Long> ownedUsers=new ArrayList<>();
    @Override Fixture fixture() { var f=super.fixture(); ownedFixtures.add(f); return f; }
    @Override UserAuth customer(String prefix) { var u=super.customer(prefix); ownedUsers.add(u.user().id()); return u; }
    @Override com.busgo.common.security.CurrentUser actor(Fixture f,RoleCode role) { var u=super.actor(f,role); ownedUsers.add(u.id()); return u; }
    @org.springframework.test.context.transaction.AfterTransaction
    void cleanCommittedFixtures() {
        for(var f:ownedFixtures) NotificationTestCleanup.fixture(jdbc,f);
        for(long id:ownedUsers) {
            jdbc.update("DELETE FROM notification_preferences WHERE user_id=?",id);
            jdbc.update("DELETE FROM refresh_tokens WHERE user_id=?",id);
            jdbc.update("DELETE FROM user_roles WHERE user_id=?",id);
            jdbc.update("DELETE FROM users WHERE id=?",id);
        }
    }
    @BeforeEach void email() {
        when(mail.configured()).thenReturn(true);
        var tx=new org.springframework.transaction.support.TransactionTemplate(manager);
        tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(s->jdbc.update("UPDATE notification_deliveries SET status='SKIPPED' WHERE status IN ('PENDING','FAILED')"));
    }
    @AfterEach void clear() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    Booking entity(long id) { return em.find(Booking.class,id); }
    long events(long id,String type) { return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE booking_id=? AND event_type=? AND audience='CUSTOMER'",Long.class,id,type); }
    long deliveryCount(long id) { return jdbc.queryForObject("SELECT COUNT(*) FROM notification_deliveries d JOIN notifications n ON n.id=d.notification_id WHERE n.booking_id=?",Long.class,id); }
    long notificationId(long id) { return jdbc.queryForObject("SELECT MIN(id) FROM notifications WHERE booking_id=? AND audience='CUSTOMER'",Long.class,id); }
    void commit() { TestTransaction.flagForCommit(); TestTransaction.end(); }
    void resume() { TestTransaction.start(); }
    void pickup(long id,int hours) { var time=LocalDateTime.of(2030,9,19,0,0).plusHours(hours); var b=entity(id); b.getPickupTripStop().setPlannedDepartureTime(time); em.flush(); }
    Instant reminderNow() { return Instant.parse("2030-09-19T00:00:00Z"); }

    @Test void paymentConfirmationAndRetryProduceOneLogicalEventEach() {
        var f=fixture(); var owner=customer("m23-payment"); var b=web(f,owner,0);
        assertThat(events(b.bookingId(),"BOOKING_CONFIRMED")).isZero();
        paymentService.confirm(owner.user(),b.bookingId()); paymentService.confirm(owner.user(),b.bookingId());
        notifications.record(entity(b.bookingId()),NotificationType.BOOKING_CONFIRMED,"once");
        assertThat(events(b.bookingId(),"BOOKING_CONFIRMED")).isOne();
        assertThat(events(b.bookingId(),"PAYMENT_SUCCEEDED")).isOne();
        assertThat(deliveryCount(b.bookingId())).isEqualTo(2); verify(mail,never()).send(any(),any(),any());
    }
    @Test void rollbackLeavesNoEventOrDeliveryForWorker() {
        var f=fixture(); var owner=customer("m23-rollback"); var b=web(f,owner,0);
        paymentService.confirm(owner.user(),b.bookingId()); long id=b.bookingId();
        assertThat(deliveryCount(id)).isEqualTo(2);
        TestTransaction.flagForRollback(); TestTransaction.end(); worker.run(); resume();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE booking_id=?",Long.class,id)).isZero();
        verify(mail,never()).send(eq("m20@example.test"),any(),any());
    }
    @Test void m20OnlyCompletionNotQuoteOrHoldNotifiesAndRepeatedCompletionIsUnique() {
        var f=expanded(); var owner=customer("m23-modify"); authenticate(owner.user()); var b=web(f,owner,0); paymentService.confirm(owner.user(),b.bookingId());
        var r=seatRequest(b.bookingId(),f,3); modifications.quote(owner.user(),b.bookingId(),false,r);
        var m=modifications.create(owner.user(),b.bookingId(),false,r);
        assertThat(events(b.bookingId(),"BOOKING_MODIFIED")).isZero();
        modifications.confirm(owner.user(),b.bookingId(),false,m.id()); modifications.confirm(owner.user(),b.bookingId(),false,m.id());
        assertThat(events(b.bookingId(),"BOOKING_MODIFIED")).isOne();
    }
    @Test void m21ExecutionNotQuoteNotifiesOnceAndContextExcludesCancelledSeats() {
        var f=expanded(); var owner=customer("m23-partial"); authenticate(owner.user()); var b=web(f,owner,0,1,2); paymentService.confirm(owner.user(),b.bookingId());
        var r=new PartialCancellationDtos.Request(List.of(itemIds(b.bookingId()).get(0))); partial.quote(owner.user(),b.bookingId(),false,r);
        assertThat(events(b.bookingId(),"PARTIAL_CANCELLATION_COMPLETED")).isZero();
        var c=partial.execute(owner.user(),b.bookingId(),false,r);
        notifications.record(entity(b.bookingId()),NotificationType.PARTIAL_CANCELLATION_COMPLETED,Long.toString(c.id()));
        assertThat(events(b.bookingId(),"PARTIAL_CANCELLATION_COMPLETED")).isOne();
        assertThat(notifications.list(owner.user(),false,0,20).data().get(0).message()).contains("A02", "A03").doesNotContain("A01");
    }
    @Test void fullCancellationCreatesOneEvent() {
        var f=fixture(); var owner=customer("m23-cancel"); authenticate(owner.user()); var b=web(f,owner,0); paymentService.confirm(owner.user(),b.bookingId());
        cancellations.customerCancel(owner.user(),b.bookingId(),new CancellationDtos.Request(null));
        cancellations.customerCancel(owner.user(),b.bookingId(),new CancellationDtos.Request(null));
        assertThat(events(b.bookingId(),"BOOKING_CANCELLED")).isOne();
    }
    @Test void unreadCountAndSingleReadAreIdempotent() {
        var f=fixture(); var owner=customer("m23-read"); var b=web(f,owner,0); paymentService.confirm(owner.user(),b.bookingId());
        assertThat(notifications.unread(owner.user(),false)).isEqualTo(2);
        long nid=notificationId(b.bookingId()); notifications.mark(owner.user(),false,nid); notifications.mark(owner.user(),false,nid);
        assertThat(notifications.unread(owner.user(),false)).isOne();
        assertThat(notifications.list(owner.user(),false,0,20).data().stream().filter(n->n.id()==nid).findFirst().orElseThrow().readAt()).isNotNull();
    }
    @Test void markAllRead() {
        var f=fixture(); var owner=customer("m23-all"); var b=web(f,owner,0); paymentService.confirm(owner.user(),b.bookingId());
        notifications.mark(owner.user(),false,null); assertThat(notifications.unread(owner.user(),false)).isZero();
    }
    @Test void foreignOwnershipIsHiddenAcrossReadHistoryAndRetry() throws Exception {
        var f=fixture(); var owner=customer("m23-owned"); var other=customer("m23-foreign"); var b=web(f,owner,0); paymentService.confirm(owner.user(),b.bookingId()); long nid=notificationId(b.bookingId());
        mvc.perform(patch("/api/v1/notifications/{id}/read",nid).header("Authorization",bearer(other.user()))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/notifications/{id}/deliveries",nid).header("Authorization",bearer(other.user()))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/notifications/{id}/retry",nid).header("Authorization",bearer(other.user()))).andExpect(status().isNotFound());
        assertThat(notifications.list(other.user(),false,0,20).data()).isEmpty();
    }
    @Test void paginationIsBoundedAndValidated() throws Exception {
        var f=fixture(); var owner=customer("m23-page"); var b=web(f,owner,0); paymentService.confirm(owner.user(),b.bookingId());
        assertThat(notifications.list(owner.user(),false,0,1).data()).hasSize(1);
        assertThat(notifications.list(owner.user(),false,1,1).data()).hasSize(1);
        assertThat(notifications.list(owner.user(),false,0,1).pagination().totalElements()).isEqualTo(2);
        mvc.perform(get("/api/v1/notifications?size=101").header("Authorization",bearer(owner.user()))).andExpect(status().isBadRequest());
    }
    @Test void disabledEmailDoesNotAffectBusinessOrInApp() {
        when(mail.configured()).thenReturn(false); var f=fixture(); var owner=customer("m23-disabled"); var b=web(f,owner,0); paymentService.confirm(owner.user(),b.bookingId());
        assertThat(events(b.bookingId(),"BOOKING_CONFIRMED")).isOne(); assertThat(deliveryCount(b.bookingId())).isZero();
    }
    @Test void smtpFailureDoesNotChangeBookingAndExistingDeliveryRetriesOnceThenStaysSent() {
        var f=fixture(); var owner=customer("m23-smtp"); var b=web(f,owner,0); paymentService.confirm(owner.user(),b.bookingId()); long id=b.bookingId();
        doThrow(new IllegalStateException("password=never-persist-this")).when(mail).send(any(),any(),any()); commit(); worker.run(); resume();
        assertThat(jdbc.queryForList("SELECT status FROM notification_deliveries d JOIN notifications n ON n.id=d.notification_id WHERE n.booking_id=?",String.class,id)).containsOnly("FAILED");
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,id)).isEqualTo("CONFIRMED");
        assertThat(notifications.deliveries(owner.user(),false,notificationId(id)).get(0).errorSummary()).isEqualTo("Email delivery failed");
        jdbc.update("UPDATE notification_deliveries d JOIN notifications n ON n.id=d.notification_id SET d.next_attempt_at=? WHERE n.booking_id=?",parameter(LocalDateTime.of(2020,1,1,0,0)),id);
        commit(); doNothing().when(mail).send(any(),any(),any()); worker.run(); clearInvocations(mail); worker.run(); resume();
        assertThat(notifications.deliveries(owner.user(),false,notificationId(id)).get(0).status()).isEqualTo("SENT");
        assertThat(notifications.deliveries(owner.user(),false,notificationId(id)).get(0).attemptCount()).isEqualTo(2);
        verify(mail,never()).send(any(),any(),any());
    }
    @Test void maximumRetryLimitIsEnforced() {
        var f=fixture(); var owner=customer("m23-limit"); var b=web(f,owner,0); paymentService.confirm(owner.user(),b.bookingId());
        jdbc.update("UPDATE notification_deliveries d JOIN notifications n ON n.id=d.notification_id SET d.status='FAILED',d.attempt_count=5 WHERE n.booking_id=?",b.bookingId());
        notifications.retry(owner.user(),false,notificationId(b.bookingId())); commit(); clearInvocations(mail); worker.run(); resume();
        verify(mail,never()).send(any(),any(),any());
    }
    @ParameterizedTest @ValueSource(ints={24,2}) void reminderGeneratedExactlyOnceAtSelectedPickup(int hours) {
        var f=fixture(); var owner=customer("m23-reminder"); var b=bookingService.create(owner.user(),new BookingDtos.CreateBookingRequest(hold(owner,f,1,3,0).holdToken(),"Customer","0901234567","m23@example.test"));
        paymentService.confirm(owner.user(),b.bookingId()); pickup(b.bookingId(),hours); commit(); reminders.runAt(reminderNow()); reminders.runAt(reminderNow()); resume();
        assertThat(events(b.bookingId(),"TRIP_REMINDER_"+hours+"H")).isOne();
        assertThat(jdbc.queryForObject("SELECT message FROM notifications WHERE booking_id=? AND event_type=? AND audience='CUSTOMER'",String.class,b.bookingId(),"TRIP_REMINDER_"+hours+"H")).contains(f.locations().get(1).getName());
    }
    @Test void cancelledBookingHasNoReminder() {
        var f=fixture(); var owner=customer("m23-no-reminder"); authenticate(owner.user()); var b=web(f,owner,0); paymentService.confirm(owner.user(),b.bookingId());
        cancellations.customerCancel(owner.user(),b.bookingId(),new CancellationDtos.Request(null)); pickup(b.bookingId(),24); commit(); reminders.runAt(reminderNow()); resume();
        assertThat(events(b.bookingId(),"TRIP_REMINDER_24H")).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"BOARDING","DEPARTED"})
    void laterSelectedPickupStillReceivesReminderAfterOriginOperationsBegin(String state) {
        var f=fixture(); var owner=customer("m23-later-pickup");
        var b=bookingService.create(owner.user(),new BookingDtos.CreateBookingRequest(hold(owner,f,1,3,0).holdToken(),"Customer","0901234567","m23@example.test"));
        paymentService.confirm(owner.user(),b.bookingId()); pickup(b.bookingId(),2);
        f.trip().setStatus(com.busgo.trip.entity.TripStatus.valueOf(state)); em.flush();
        commit(); reminders.runAt(reminderNow()); resume();
        assertThat(events(b.bookingId(),"TRIP_REMINDER_2H")).isOne();
    }
    @Test void m20ReminderUsesCurrentTripAndSeat() {
        var f=expanded(); var owner=customer("m23-current"); authenticate(owner.user()); var b=web(f,owner,0); paymentService.confirm(owner.user(),b.bookingId());
        var target=targetTrip(f); var m=modifications.create(owner.user(),b.bookingId(),false,tripRequest(b.bookingId(),target)); modifications.confirm(owner.user(),b.bookingId(),false,m.id());
        pickup(b.bookingId(),24); commit(); reminders.runAt(reminderNow()); resume();
        assertThat(events(b.bookingId(),"TRIP_REMINDER_24H")).isOne();
        assertThat(entity(b.bookingId()).getTrip().getId()).isEqualTo(target.getId());
    }
    @Test void partialReminderUsesRemainingSeatsAndTotal() {
        var f=expanded(); var owner=customer("m23-current-partial"); authenticate(owner.user()); var b=web(f,owner,0,1); paymentService.confirm(owner.user(),b.bookingId());
        partial.execute(owner.user(),b.bookingId(),false,new PartialCancellationDtos.Request(List.of(itemIds(b.bookingId()).get(0)))); pickup(b.bookingId(),2); commit(); reminders.runAt(reminderNow()); resume();
        assertThat(jdbc.queryForObject("SELECT message FROM notifications WHERE booking_id=? AND event_type='TRIP_REMINDER_2H' AND audience='CUSTOMER'",String.class,b.bookingId())).contains("A02","200").doesNotContain("A01");
    }
    @Test void lateBookingDoesNotGetHistorical24hButCanGet2h() {
        var f=fixture(); var owner=customer("m23-late"); var b=web(f,owner,0); paymentService.confirm(owner.user(),b.bookingId()); pickup(b.bookingId(),24);
        jdbc.update("UPDATE bookings SET created_at=? WHERE id=?",parameter(LocalDateTime.of(2030,9,19,0,1)),b.bookingId()); commit(); reminders.runAt(reminderNow().plusSeconds(300)); resume();
        assertThat(events(b.bookingId(),"TRIP_REMINDER_24H")).isZero(); commit(); reminders.runAt(reminderNow().plusSeconds(22*3600)); resume(); assertThat(events(b.bookingId(),"TRIP_REMINDER_2H")).isOne();
    }
    @Test void preferencesSuppressReminderEmailButKeepInApp() {
        var f=fixture(); var owner=customer("m23-prefs"); notifications.savePreferences(owner.user(),new Preferences(true,true,false));
        var b=web(f,owner,0); paymentService.confirm(owner.user(),b.bookingId()); pickup(b.bookingId(),2); commit(); reminders.runAt(reminderNow()); resume();
        assertThat(events(b.bookingId(),"TRIP_REMINDER_2H")).isOne(); assertThat(deliveryCount(b.bookingId())).isEqualTo(2);
    }
    @Test void queuedEmailChecksChangedPreferenceBeforeSend() {
        var f=fixture(); var owner=customer("m23-queued"); var b=web(f,owner,0); paymentService.confirm(owner.user(),b.bookingId()); notifications.savePreferences(owner.user(),new Preferences(false,true,true)); commit(); worker.run(); resume();
        assertThat(notifications.deliveries(owner.user(),false,notificationId(b.bookingId())).get(0).status()).isEqualTo("SKIPPED");
    }
    @Test void phoneEmailOnlyAndAdminInboxWithoutFakeCustomer() {
        var f=fixture(); var admin=actor(f,RoleCode.OPERATOR_ADMIN); authenticate(admin);
        var r=request(f,0,2,PaymentMethod.PAY_ON_BOARD);
        var b=assisted.create(admin,new AssistedBookingDtos.CreateRequest(r.tripId(),r.pickupLocationId(),r.dropoffLocationId(),r.tripSeatIds(),r.contactName(),r.contactPhone(),"phone@example.test",r.paymentMethod()));
        assertThat(entity(b.bookingId()).getCustomer()).isNull(); assertThat(events(b.bookingId(),"BOOKING_CONFIRMED")).isZero(); assertThat(deliveryCount(b.bookingId())).isOne();
        assertThat(notifications.unread(admin,true)).isOne();
        assisted.record(admin,b.bookingId(),new AssistedBookingDtos.RecordPayment(PaymentMethod.PAY_ON_BOARD,null));
        assertThat(notifications.unread(admin,true)).isOne(); // Payment is customer-only; reservation confirmation remains unique.
    }
    @Test void phoneWithoutEmailStillNotifiesOperator() {
        var f=fixture(); var admin=actor(f,RoleCode.OPERATOR_ADMIN); authenticate(admin); var b=assisted.create(admin,request(f,0,2,PaymentMethod.PAY_ON_BOARD));
        assertThat(deliveryCount(b.bookingId())).isZero(); assertThat(notifications.unread(admin,true)).isOne();
    }
    @Test void operatorIsolationAndStaffSystemAdminPublicDenial() throws Exception {
        var f=fixture(); var admin=actor(f,RoleCode.OPERATOR_ADMIN); authenticate(admin); var b=assisted.create(admin,request(f,0,2,PaymentMethod.PAY_ON_BOARD));
        var foreign=actor(fixture(),RoleCode.OPERATOR_ADMIN); authenticate(foreign); assertThat(notifications.list(foreign,true,0,20).data()).isEmpty();
        long nid=jdbc.queryForObject("SELECT MIN(id) FROM notifications WHERE booking_id=?",Long.class,b.bookingId());
        mvc.perform(patch("/api/v1/operator/notifications/{id}/read",nid).header("Authorization",bearer(foreign))).andExpect(status().isNotFound());
        for(var role:List.of(RoleCode.OPERATOR_STAFF,RoleCode.SYSTEM_ADMIN)) {
            var denied=actor(f,role); mvc.perform(get("/api/v1/operator/notifications").header("Authorization",bearer(denied))).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/notifications").header("Authorization",bearer(denied))).andExpect(status().isForbidden());
        }
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        mvc.perform(get("/api/v1/notifications").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());
    }
    @Test void validAccountlessPaymentLinkCannotAuthenticateToNotificationApis() throws Exception {
        var f=fixture(); var admin=actor(f,RoleCode.OPERATOR_ADMIN); authenticate(admin);
        var b=assisted.create(admin,request(f,0,2,PaymentMethod.QR_TRANSFER));
        String publicToken=token(assisted.issueLink(admin,b.bookingId()));
        assertThat(assisted.publicContext(publicToken).bookingCode()).isEqualTo(b.bookingCode());
        for(String path:List.of("/api/v1/notifications","/api/v1/notification-preferences","/api/v1/operator/notifications"))
            mvc.perform(get(path).header("Authorization","Bearer "+publicToken)).andExpect(status().isUnauthorized());
    }
}
