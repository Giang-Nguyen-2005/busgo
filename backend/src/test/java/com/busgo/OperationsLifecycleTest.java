package com.busgo;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.busgo.common.exception.BusinessException;
import com.busgo.common.security.CurrentUser;
import com.busgo.operations.OperationsService;
import com.busgo.operator.OperatorContextService;
import com.busgo.operator.entity.TransportOperator;
import com.busgo.trip.entity.*;
import com.busgo.trip.operations.*;
import com.busgo.trip.repository.*;
import com.busgo.user.entity.RoleCode;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

class OperationsLifecycleTest {
    private final TripRepository trips=mock(TripRepository.class);
    private final OperatorContextService context=mock(OperatorContextService.class);
    private final OperationsService operations=mock(OperationsService.class);
    private final CurrentUser actor=new CurrentUser(5L,List.of(RoleCode.OPERATOR_ADMIN));
    private final Trip trip=new Trip();
    private final OperatorTripOperationsService service=new OperatorTripOperationsService(trips,context,
            mock(OperatorOccupancyQueryRepository.class),mock(TripSeatRepository.class),mock(TripSegmentRepository.class),operations);
    @BeforeEach void setup() {
        var operator=mock(TransportOperator.class); when(operator.getId()).thenReturn(7L);
        when(context.requireAdminOperator(actor)).thenReturn(operator);
        when(trips.lockOwnedById(11L,7L)).thenReturn(Optional.of(trip)); trip.setStatus(TripStatus.SCHEDULED);
    }
    @ParameterizedTest
    @EnumSource(value=TripStatus.class,names={"BOARDING","DEPARTED","COMPLETED"})
    void readinessOrUnresolvedPickupNeverWritesLifecycleOrHistory(TripStatus target) {
        var failure=new BusinessException("NOT_READY","Not ready",HttpStatus.CONFLICT,null);
        if(target==TripStatus.BOARDING) doThrow(failure).when(operations).requireReady(11L);
        if(target==TripStatus.DEPARTED) { trip.setStatus(TripStatus.BOARDING); doThrow(failure).when(operations).requireOriginClosed(11L); }
        if(target==TripStatus.COMPLETED) { trip.setStatus(TripStatus.DEPARTED); doThrow(failure).when(operations).requireComplete(11L); }
        TripStatus before=trip.getStatus();
        assertThatThrownBy(()->service.updateStatus(actor,11L,target)).isSameAs(failure);
        assertThat(trip.getStatus()).isEqualTo(before); verify(trips,never()).flush();
        verify(operations,never()).history(anyLong(),anyString(),anyLong(),anyString(),anyLong(),any());
    }
    @Test void committedTransitionFlushesBeforeRecordingHistoryAndRepeatDoesNotDuplicateIt() {
        service.updateStatus(actor,11L,TripStatus.BOARDING); service.updateStatus(actor,11L,TripStatus.BOARDING);
        var order=inOrder(operations,trips);
        order.verify(operations).requireReady(11L); order.verify(trips).flush();
        order.verify(operations).history(11L,"TRIP",11L,"TRIP_BOARDING",5L,"SCHEDULED -> BOARDING");
        verify(operations,times(1)).requireReady(11L);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.BOARDING);
    }
    @Test void skippedLifecycleTransitionCannotReachOperationalGuards() {
        assertThatThrownBy(()->service.updateStatus(actor,11L,TripStatus.COMPLETED)).isInstanceOf(BusinessException.class);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.SCHEDULED); verifyNoInteractions(operations);
    }
}
