package com.busgo;

import static org.assertj.core.api.Assertions.*;
import com.busgo.common.security.CurrentUser;
import com.busgo.trip.entity.TripStatus;
import com.busgo.trip.operations.*;
import com.busgo.user.entity.RoleCode;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties="busgo.notifications.initial-delay=PT24H") @ActiveProfiles("dev")
class M25LiveTripConcurrencyIT extends M20Support {
    @Autowired TransactionTemplate tx;
    @Autowired LiveTripOperationsService live;
    @Autowired OperatorTripOperationsService lifecycle;
    Fixture f;CurrentUser admin;
    @BeforeEach void setup() {
        f=tx.execute(s->fixture());admin=tx.execute(s->actor(f,RoleCode.OPERATOR_ADMIN));authenticate(admin);
    }
    @AfterEach void cleanup() {
        NotificationTestCleanup.fixture(jdbc,f);
        jdbc.update("DELETE FROM user_roles WHERE user_id=?",admin.id());jdbc.update("DELETE FROM users WHERE id=?",admin.id());
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
    void update(String key,int delay) { live.update(admin,f.trip().getId(),new LiveTripOperationsService.Update(key,delay,null,null),false); }
    List<String> race(Runnable a,Runnable b) throws Exception {
        var barrier=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        try {var one=pool.submit(()->attempt(barrier,a));var two=pool.submit(()->attempt(barrier,b));return List.of(one.get(30,TimeUnit.SECONDS),two.get(30,TimeUnit.SECONDS));}
        finally { pool.shutdownNow(); }
    }
    String attempt(CyclicBarrier barrier,Runnable r) throws Exception {
        authenticate(admin);
        try {barrier.await(10,TimeUnit.SECONDS);r.run();return "OK";}
        catch(com.busgo.common.exception.BusinessException e) {return e.getCode();}
        finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }
    void preparedBoarding() {
        tx.executeWithoutResult(s->{M16BFixtures.crew(jdbc,f.operator().getId(),f.trip().getId(),admin.id());});
        lifecycle.updateStatus(admin,f.trip().getId(),TripStatus.BOARDING);
        for(var stop:f.stops()) if(stop.isAllowPickup()) jdbc.update("INSERT INTO trip_stop_operations(trip_id,stop_id,pickup_closed_at,pickup_closed_by) VALUES(?,?,UTC_TIMESTAMP(6),?)",f.trip().getId(),stop.getId(),admin.id());
    }
    @Test void concurrentDelayPublicationsSerializeStateAndHistory() throws Exception {
        assertThat(race(()->update("one",20),()->update("two",30))).containsOnly("OK");
        var h=live.history(admin,f.trip().getId());assertThat(h).hasSize(2);
        assertThat(live.current(admin,f.trip().getId()).delayMinutes()).isEqualTo(h.get(0).state().delayMinutes());
    }
    @Test void concurrentRetryCreatesOneOccurrence() throws Exception {
        assertThat(race(()->update("same",20),()->update("same",20))).containsOnly("OK");
        assertThat(live.history(admin,f.trip().getId())).hasSize(1);
    }
    @Test void delayAndDepartureHaveNoLostUpdate() throws Exception {
        preparedBoarding();assertThat(race(()->update("delay",20),()->lifecycle.updateStatus(admin,f.trip().getId(),TripStatus.DEPARTED))).containsOnly("OK");
        var state=live.current(admin,f.trip().getId());assertThat(state.lifecycle()).isEqualTo(TripStatus.DEPARTED);assertThat(state.delayMinutes()).isEqualTo(20);assertThat(state.actualDepartureAt()).isNotNull();
        assertThat(live.history(admin,f.trip().getId())).hasSize(2);
    }
    @Test void concurrentDeparturesKeepOriginalActualTime() throws Exception {
        preparedBoarding();assertThat(race(()->lifecycle.updateStatus(admin,f.trip().getId(),TripStatus.DEPARTED),()->lifecycle.updateStatus(admin,f.trip().getId(),TripStatus.DEPARTED))).containsOnly("OK");
        var time=live.current(admin,f.trip().getId()).actualDepartureAt();lifecycle.updateStatus(admin,f.trip().getId(),TripStatus.DEPARTED);
        assertThat(live.current(admin,f.trip().getId()).actualDepartureAt()).isEqualTo(time);assertThat(live.history(admin,f.trip().getId())).hasSize(1);
    }
    @Test void concurrentCompletionsKeepOriginalActualTime() throws Exception {
        preparedBoarding();lifecycle.updateStatus(admin,f.trip().getId(),TripStatus.DEPARTED);
        assertThat(race(()->lifecycle.updateStatus(admin,f.trip().getId(),TripStatus.COMPLETED),()->lifecycle.updateStatus(admin,f.trip().getId(),TripStatus.COMPLETED))).containsOnly("OK");
        var time=live.current(admin,f.trip().getId()).actualArrivalAt();lifecycle.updateStatus(admin,f.trip().getId(),TripStatus.COMPLETED);
        assertThat(live.current(admin,f.trip().getId()).actualArrivalAt()).isEqualTo(time);assertThat(live.history(admin,f.trip().getId())).hasSize(2);
    }
    @Test void cancellationRaceCannotResurrectTrip() throws Exception {
        // Baseline has no trip-cancel API. Model the authoritative cancellation under its trip lock.
        var outcomes=race(()->update("delay",20),()->tx.executeWithoutResult(s->{var trip=trips.lockById(f.trip().getId()).orElseThrow();trip.setStatus(TripStatus.CANCELLED);trips.flush();}));
        assertThat(outcomes).contains("OK").allMatch(x->Set.of("OK","INVALID_OPERATIONAL_UPDATE").contains(x));
        assertThat(live.current(admin,f.trip().getId()).lifecycle()).isEqualTo(TripStatus.CANCELLED);
        assertThatThrownBy(()->update("later",30)).hasMessageContaining("Terminal");
    }
}
