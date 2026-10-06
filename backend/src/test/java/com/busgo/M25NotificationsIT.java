package com.busgo;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.busgo.booking.*;
import com.busgo.notification.*;
import com.busgo.trip.operations.LiveTripOperationsService;
import com.busgo.user.entity.RoleCode;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties="busgo.notifications.initial-delay=PT24H") @ActiveProfiles("dev")
class M25NotificationsIT extends M20Support {
    @Autowired TransactionTemplate tx;
    @Autowired LiveTripOperationsService live;
    @Autowired NotificationService notifications;
    @Autowired TripReminderJob reminders;
    @MockitoBean NotificationMailSender mail;
    Fixture f;com.busgo.common.security.CurrentUser admin;
    final List<Long> customerIds=new ArrayList<>();
    @BeforeEach void setup() { f=tx.execute(s->fixture());admin=tx.execute(s->actor(f,RoleCode.OPERATOR_ADMIN));authenticate(admin);when(mail.configured()).thenReturn(true); }
    @AfterEach void cleanup() {
        NotificationTestCleanup.fixture(jdbc,f);
        var ids=new ArrayList<>(customerIds);ids.add(admin.id());
        for(long id:ids) {jdbc.update("DELETE FROM notification_preferences WHERE user_id=?",id);jdbc.update("DELETE FROM refresh_tokens WHERE user_id=?",id);jdbc.update("DELETE FROM user_roles WHERE user_id=?",id);jdbc.update("DELETE FROM users WHERE id=?",id);}
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
    UserAuth owner() {var owner=tx.execute(s->customer("m25-notification"));customerIds.add(owner.user().id());return owner;}
    long deliveries(long booking) {return jdbc.queryForObject("SELECT COUNT(*) FROM notification_deliveries d JOIN notifications n ON n.id=d.notification_id WHERE n.booking_id=? AND n.event_type LIKE 'TRIP_%'",Long.class,booking);}
    @Test void accountlessPhoneEmailUsesCommittedOutboxAndNeverSendsInsideBusinessTransaction() {
        var b=assisted.create(admin,new AssistedBookingDtos.CreateRequest(f.trip().getId(),f.locations().get(1).getId(),f.locations().get(3).getId(),List.of(f.seats().get(0).getId()),"Caller","0900000000","phone@example.test",com.busgo.payment.entity.PaymentMethod.PAY_ON_BOARD));
        live.update(admin,f.trip().getId(),new LiveTripOperationsService.Update("phone",20,"Traffic",null),false);
        assertThat(deliveries(b.bookingId())).isOne();verify(mail,never()).send(any(),any(),any());
        assertThat(jdbc.queryForObject("SELECT message FROM notifications WHERE booking_id=? AND event_type='TRIP_DELAYED'",String.class,b.bookingId())).contains("09:20","Traffic",b.bookingCode());
    }
    @Test void disabledEmailPreferenceStillCreatesInboxAndClearEvent() {
        var owner=owner();var b=tx.execute(s->web(f,owner,0));paymentService.confirm(owner.user(),b.bookingId());authenticate(owner.user());notifications.savePreferences(owner.user(),new NotificationDtos.Preferences(true,false,true));authenticate(admin);
        live.update(admin,f.trip().getId(),new LiveTripOperationsService.Update("delay",20,null,null),false);
        live.update(admin,f.trip().getId(),new LiveTripOperationsService.Update("clear",0,null,null),false);
        assertThat(deliveries(b.bookingId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE booking_id=? AND event_type IN ('TRIP_DELAYED','TRIP_DELAY_CLEARED')",Long.class,b.bookingId())).isEqualTo(2);
    }
    @Test void rollbackLeavesNoCurrentStateHistoryOrOperationalNotification() {
        var owner=owner();var b=tx.execute(s->web(f,owner,0));paymentService.confirm(owner.user(),b.bookingId());
        tx.executeWithoutResult(s->{live.update(admin,f.trip().getId(),new LiveTripOperationsService.Update("rollback",20,null,null),false);s.setRollbackOnly();});
        assertThat(live.current(admin,f.trip().getId()).delayMinutes()).isZero();assertThat(live.history(admin,f.trip().getId())).isEmpty();assertThat(deliveries(b.bookingId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE booking_id=? AND event_type='TRIP_DELAYED'",Long.class,b.bookingId())).isZero();
    }
    @Test void delayDoesNotResendReminderAndNewReminderUsesCurrentPickup() {
        var owner=owner();var b=tx.execute(s->web(f,owner,0));paymentService.confirm(owner.user(),b.bookingId());
        jdbc.update("UPDATE bookings SET created_at='2020-01-01' WHERE id=?",b.bookingId());
        var instant=f.stops().get(0).getPlannedDepartureTime().minusHours(2).toInstant(ZoneOffset.UTC);
        reminders.runAt(instant);
        live.update(admin,f.trip().getId(),new LiveTripOperationsService.Update("first",20,null,null),false);reminders.runAt(instant);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE booking_id=? AND event_type='TRIP_REMINDER_2H'",Long.class,b.bookingId())).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE booking_id=? AND event_type='TRIP_DELAYED'",Long.class,b.bookingId())).isOne();
        reminders.runAt(f.stops().get(0).getPlannedDepartureTime().minusHours(24).toInstant(ZoneOffset.UTC));
        assertThat(jdbc.queryForObject("SELECT message FROM notifications WHERE booking_id=? AND event_type='TRIP_REMINDER_24H'",String.class,b.bookingId())).contains("08:20");
    }
}
