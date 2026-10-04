package com.busgo;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.busgo.fleet.*;
import com.busgo.fleet.MaintenanceDtos.*;
import com.busgo.fleet.entity.BusStatus;
import com.busgo.common.security.CurrentUser;
import com.busgo.trip.TripAggregateCreator;
import com.busgo.operations.OperationsService;
import com.busgo.user.entity.RoleCode;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("dev") @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
class M18CFleetMaintenanceIT extends M16ATestSupport {
    @Autowired MaintenanceService maintenance;
    @Autowired BusService fleet;
    @Autowired TripAggregateCreator creator;
    @Autowired OperationsService operations;
    @Autowired FleetMaintenanceGuard guard;
    @Autowired MockMvc mvc;
    @AfterEach void clear() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    private Schedule schedule(LocalDateTime start,LocalDateTime end) { return new Schedule(Type.PERIODIC_SERVICE,"Bảo dưỡng", "Ghi chú vận hành",start.atOffset(ZoneOffset.UTC),end.atOffset(ZoneOffset.UTC)); }
    private Maintenance planned(Fixture f,CurrentUser actor) { return maintenance.schedule(actor,f.trip().getBus().getId(),schedule(f.trip().getDepartureTime().minusDays(2),f.trip().getDepartureTime().minusDays(1))); }
    @Test void createStartCompleteKeepsRecordAndStatusHistory() {
        var f=fixture();var admin=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(admin);long bus=f.trip().getBus().getId();
        var m=planned(f,admin);assertThat(m.status()).isEqualTo(Status.SCHEDULED);assertThat(fleet.get(admin,bus).readiness().operationalReady()).isTrue();
        assertThat(maintenance.start(admin,m.id()).status()).isEqualTo(Status.IN_PROGRESS);
        assertThat(fleet.get(admin,bus).status()).isEqualTo(BusStatus.MAINTENANCE);assertThat(fleet.get(admin,bus).readiness().operationalReady()).isFalse();
        var done=maintenance.complete(admin,m.id(),new Complete(100L,LocalDate.of(2030,10,1),200L,"Đã kiểm tra"));
        assertThat(done.completedAt()).isNotNull();assertThat(done.completedBy()).isEqualTo(admin.id());assertThat(done.status()).isEqualTo(Status.COMPLETED);
        assertThat(fleet.get(admin,bus).status()).isEqualTo(BusStatus.AVAILABLE);
        assertThat(guard.warning(bus,m.scheduledStart().toLocalDateTime(),m.scheduledEnd().toLocalDateTime())).isEmpty();
        assertThat(maintenance.history(admin,bus)).hasSize(2);
        assertThat(maintenance.list(admin,bus,null,null,null,null,0,20).data()).extracting(Maintenance::id).contains(m.id());
    }
    @Test void invalidTransitionsAndRepeatCompletionRejectWithoutChangingTimestamp() {
        var f=fixture();var a=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(a);var m=planned(f,a);
        assertThatThrownBy(()->maintenance.complete(a,m.id(),new Complete(null,null,null,null))).hasMessageContaining("SCHEDULED");
        maintenance.start(a,m.id());assertThatThrownBy(()->maintenance.cancel(a,m.id(),new Cancel(null))).hasMessageContaining("IN_PROGRESS");
        var done=maintenance.complete(a,m.id(),new Complete(null,null,null,null));
        assertThatThrownBy(()->maintenance.complete(a,m.id(),new Complete(null,null,null,null))).hasMessageContaining("COMPLETED");
        assertThat(maintenance.list(a,m.busId(),null,null,null,null,0,20).data().get(0).completedAt()).isEqualTo(done.completedAt());
    }
    @Test void cancelPreservesReasonAndNoLongerConflicts() {
        var f=fixture();var a=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(a);var m=planned(f,a);
        var cancelled=maintenance.cancel(a,m.id(),new Cancel("Đổi kế hoạch"));assertThat(cancelled.cancelledAt()).isNotNull();assertThat(cancelled.cancellationReason()).isEqualTo("Đổi kế hoạch");
        assertThat(guard.warning(m.busId(),m.scheduledStart().toLocalDateTime(),m.scheduledEnd().toLocalDateTime())).isEmpty();
        assertThatThrownBy(()->maintenance.start(a,m.id())).hasMessageContaining("CANCELLED");
    }
    @Test void overlappingTripRejectsWithSafeDetailsAndBoundariesAllow() {
        var f=fixture();var a=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(a);var t=f.trip();
        assertThatThrownBy(()->maintenance.schedule(a,t.getBus().getId(),schedule(t.getDepartureTime(),t.getEstimatedArrivalTime()))).isInstanceOf(com.busgo.common.exception.BusinessException.class).satisfies(ex->{var e=(com.busgo.common.exception.BusinessException)ex;assertThat(e.getStatus().value()).isEqualTo(409);assertThat(e.getDetails().toString()).contains(t.getId().toString());});
        maintenance.schedule(a,t.getBus().getId(),schedule(t.getDepartureTime().minusHours(1),t.getDepartureTime()));
        maintenance.schedule(a,t.getBus().getId(),schedule(t.getEstimatedArrivalTime(),t.getEstimatedArrivalTime().plusHours(1)));
        assertThat(t.getStatus()).isEqualTo(com.busgo.trip.entity.TripStatus.SCHEDULED);
    }
    @Test void scheduledMaintenanceGuardsTripCreationAndCancelledRecordAllowsIt() {
        var f=fixture();var a=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(a);var t=f.trip();
        var departure=t.getEstimatedArrivalTime().plusDays(1);var m=maintenance.schedule(a,t.getBus().getId(),schedule(departure,departure.plusHours(3)));
        assertThatThrownBy(()->creator.create(f.operator().getId(),t.getOperatorRoute().getId(),t.getBus().getId(),departure)).hasMessageContaining("bảo trì");
        maintenance.cancel(a,m.id(),new Cancel(null));assertThat(creator.create(f.operator().getId(),t.getOperatorRoute().getId(),t.getBus().getId(),departure).trip()).isNotNull();
    }
    @Test void inProgressBlocksAssignmentEvenOutsidePlannedWindowAndBoarding() {
        var f=fixture();var a=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(a);var m=planned(f,a);maintenance.start(a,m.id());
        assertThatThrownBy(()->creator.create(f.operator().getId(),f.trip().getOperatorRoute().getId(),m.busId(),f.trip().getEstimatedArrivalTime().plusDays(3))).hasMessageContaining("AVAILABLE");
        assertThatThrownBy(()->operations.requireReady(f.trip().getId())).hasMessageContaining("Xe chưa");
        assertThatThrownBy(()->fleet.update(a,m.busId(),new FleetDtos.UpdateBusRequest(null,null,BusStatus.AVAILABLE))).hasMessageContaining("maintenance");
    }
    @Test void activeTripBlocksStartingEvenWhenPlannedTimesDoNotOverlap() {
        var f=fixture();var a=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(a);var m=planned(f,a);
        f.trip().setStatus(com.busgo.trip.entity.TripStatus.DEPARTED);trips.flush();
        assertThatThrownBy(()->maintenance.start(a,m.id())).hasMessageContaining("chuyến");
        assertThat(fleet.get(a,m.busId()).status()).isEqualTo(BusStatus.AVAILABLE);
    }
    @Test void originallyInactiveBusDoesNotReactivateAndManualInactiveIsPreserved() {
        var f=fixture();var a=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(a);long bus=f.trip().getBus().getId();
        fleet.update(a,bus,new FleetDtos.UpdateBusRequest(null,null,BusStatus.INACTIVE));var m=planned(f,a);maintenance.start(a,m.id());maintenance.complete(a,m.id(),new Complete(null,null,null,null));
        assertThat(fleet.get(a,bus).status()).isEqualTo(BusStatus.INACTIVE);
        fleet.update(a,bus,new FleetDtos.UpdateBusRequest(null,null,BusStatus.AVAILABLE));m=planned(f,a);maintenance.start(a,m.id());fleet.update(a,bus,new FleetDtos.UpdateBusRequest(null,null,BusStatus.INACTIVE));maintenance.complete(a,m.id(),new Complete(null,null,null,null));assertThat(fleet.get(a,bus).status()).isEqualTo(BusStatus.INACTIVE);
    }
    @Test void readinessUsesExplicitDueOnlyAndFutureScheduleDoesNotDisableBus() {
        var f=fixture();var a=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(a);var m=planned(f,a);
        assertThat(fleet.get(a,m.busId()).readiness().warnings()).doesNotContain("Bảo trì quá hạn");assertThat(fleet.get(a,m.busId()).readiness().operationalReady()).isTrue();
        maintenance.start(a,m.id());maintenance.complete(a,m.id(),new Complete(null,null,1L,null));
        assertThat(fleet.get(a,m.busId()).readiness().warnings()).doesNotContain("Bảo trì quá hạn");
        jdbc.update("UPDATE bus_maintenance_records SET next_due_date=? WHERE id=?",LocalDate.now(com.busgo.common.time.BusGoTime.BUSINESS_ZONE).minusDays(1),m.id());
        assertThat(fleet.get(a,m.busId()).readiness().warnings()).contains("Bảo trì quá hạn");
        jdbc.update("UPDATE bus_maintenance_records SET next_due_date=? WHERE id=?",LocalDate.now(com.busgo.common.time.BusGoTime.BUSINESS_ZONE).plusDays(7),m.id());
        assertThat(fleet.get(a,m.busId()).readiness().warnings()).contains("Bảo trì sắp tới");
    }
    @Test void foreignOperatorCannotReadOrMutateAnyFleetHistoryOrMaintenance() {
        var f=fixture();var foreign=fixture();var a=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(a);var m=planned(f,a);var other=actor(foreign,RoleCode.OPERATOR_ADMIN);authenticate(other);
        assertThatThrownBy(()->maintenance.start(other,m.id())).isInstanceOf(com.busgo.common.exception.ResourceNotFoundException.class);
        assertThatThrownBy(()->maintenance.list(other,m.busId(),null,null,null,null,0,20)).isInstanceOf(com.busgo.common.exception.ResourceNotFoundException.class);
        assertThatThrownBy(()->maintenance.history(other,m.busId())).isInstanceOf(com.busgo.common.exception.ResourceNotFoundException.class);
        assertThatThrownBy(()->maintenance.assignedTrips(other,m.busId(),0,20)).isInstanceOf(com.busgo.common.exception.ResourceNotFoundException.class);
        assertThat(maintenance.list(other,null,null,null,null,null,0,20).data()).isEmpty();
    }
    @Test void rolePolicyDeniesStaffSystemCustomerAndAnonymous() throws Exception {
        var f=fixture();var a=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(a);var m=planned(f,a);
        for(var role:List.of(RoleCode.OPERATOR_STAFF,RoleCode.SYSTEM_ADMIN,RoleCode.CUSTOMER)) {
            var denied=actor(f,role);org.springframework.security.core.context.SecurityContextHolder.clearContext();
            mvc.perform(get("/api/v1/operator/maintenance").header("Authorization",bearer(denied))).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/operator/maintenance/{id}/start",m.id()).header("Authorization",bearer(denied))).andExpect(status().isForbidden());
        }
        org.springframework.security.core.context.SecurityContextHolder.clearContext();mvc.perform(get("/api/v1/operator/maintenance")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/operator/buses/{id}",m.busId()).header("Authorization",bearer(a))).andExpect(status().isOk()).andExpect(jsonPath("$.data.readiness.operationalReady").value(true));
    }
    @Test void invalidWindowDueAndOdometerReject() {
        var f=fixture();var a=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(a);
        assertThatThrownBy(()->maintenance.schedule(a,f.trip().getBus().getId(),schedule(f.trip().getDepartureTime(),f.trip().getDepartureTime()))).hasMessageContaining("end");
        var m=planned(f,a);maintenance.start(a,m.id());
        assertThatThrownBy(()->maintenance.complete(a,m.id(),new Complete(-1L,null,null,null))).hasMessageContaining("due");
        assertThatThrownBy(()->maintenance.complete(a,m.id(),new Complete(100L,null,50L,null))).hasMessageContaining("due");
    }
    @Test void scheduledConflictComposesWithCrewAndCancellingRevealsCrewWarning() {
        var f=fixture();var a=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(a);var m=planned(f,a);
        jdbc.update("UPDATE bus_maintenance_records SET scheduled_start=?,scheduled_end=? WHERE id=?",com.busgo.common.time.JpaJdbcTime.parameter(f.trip().getDepartureTime()),com.busgo.common.time.JpaJdbcTime.parameter(f.trip().getEstimatedArrivalTime()),m.id());
        assertThat(operations.crew(a,f.trip().getId()).get("warning").toString()).contains("Lịch bảo trì");
        assertThatThrownBy(()->operations.requireReady(f.trip().getId())).hasMessageContaining("Lịch bảo trì");
        maintenance.cancel(a,m.id(),new Cancel(null));assertThat(operations.crew(a,f.trip().getId()).get("warning").toString()).contains("tài xế");
    }
    @Test void explicitManualMaintenanceDoesNotAutoRestoreAndNoopHasNoFakeHistory() {
        var f=fixture();var a=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(a);long bus=f.bus().getId();
        fleet.update(a,bus,new FleetDtos.UpdateBusRequest(null,null,BusStatus.AVAILABLE));assertThat(maintenance.history(a,bus)).isEmpty();
        fleet.update(a,bus,new FleetDtos.UpdateBusRequest(null,null,BusStatus.MAINTENANCE));var m=planned(f,a);maintenance.start(a,m.id());maintenance.complete(a,m.id(),new Complete(null,null,null,null));
        assertThat(fleet.get(a,bus).status()).isEqualTo(BusStatus.MAINTENANCE);assertThat(maintenance.history(a,bus)).hasSize(1);
    }
    @Test void unrelatedServiceDoesNotEraseDueAndSameTypeCompletionSupersedesIt() {
        var f=fixture();var a=actor(f,RoleCode.OPERATOR_ADMIN);authenticate(a);var m=planned(f,a);maintenance.start(a,m.id());maintenance.complete(a,m.id(),new Complete(null,null,null,null));
        jdbc.update("UPDATE bus_maintenance_records SET next_due_date=? WHERE id=?",LocalDate.now(com.busgo.common.time.BusGoTime.BUSINESS_ZONE).minusDays(1),m.id());
        var input=schedule(f.trip().getDepartureTime().minusDays(2),f.trip().getDepartureTime().minusDays(1));
        var other=maintenance.schedule(a,m.busId(),new Schedule(Type.TIRE,"Lốp",null,input.scheduledStart(),input.scheduledEnd()));maintenance.start(a,other.id());maintenance.complete(a,other.id(),new Complete(null,null,null,null));
        assertThat(fleet.get(a,m.busId()).readiness().warnings()).contains("Bảo trì quá hạn");
        var next=planned(f,a);maintenance.start(a,next.id());maintenance.complete(a,next.id(),new Complete(null,null,null,null));
        assertThat(fleet.get(a,m.busId()).readiness().warnings()).doesNotContain("Bảo trì quá hạn");
    }
}
