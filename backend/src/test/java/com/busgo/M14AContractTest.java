package com.busgo;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.busgo.common.exception.BusinessException;
import com.busgo.common.security.CurrentUser;
import com.busgo.fleet.entity.SeatType;
import com.busgo.operator.OperatorContextService;
import com.busgo.operator.entity.TransportOperator;
import com.busgo.trip.TripAggregateCreator;
import com.busgo.trip.TripService;
import com.busgo.trip.entity.*;
import com.busgo.trip.operations.*;
import com.busgo.trip.repository.*;
import com.busgo.user.entity.RoleCode;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;

class M14AContractTest {
    private final TripRepository trips = mock(TripRepository.class);
    private final TripStopSnapshotRepository stops = mock(TripStopSnapshotRepository.class);
    private final TripSegmentRepository segments = mock(TripSegmentRepository.class);
    private final TripSeatRepository seats = mock(TripSeatRepository.class);
    private final OperatorContextService context = mock(OperatorContextService.class);
    private final TripAggregateCreator aggregateCreator = mock(TripAggregateCreator.class);
    private final CurrentUser user = new CurrentUser(10L, List.of(RoleCode.OPERATOR_ADMIN));

    @Test
    void vietnamBusinessDateUsesExactUtcHalfOpenWindow() {
        TripService service = tripService();
        memberOf(7L);
        when(trips.searchOwned(anyLong(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        service.list(user, null, LocalDate.of(2030, 9, 21), null, null, null, 0, 20);

        verify(trips).searchOwned(eq(7L), eq(LocalDateTime.of(2030, 9, 20, 17, 0)),
                eq(LocalDateTime.of(2030, 9, 21, 17, 0)), isNull(), isNull(), isNull(),
                eq(PageRequest.of(0, 20,
                        Sort.by("departureTime").ascending().and(Sort.by("id")))));
    }

    @Test
    void dateAndBusinessDateAreMutuallyExclusive() {
        assertThatThrownBy(() -> tripService().list(user, LocalDate.of(2030, 9, 21),
                LocalDate.of(2030, 9, 21), null, null, null, 0, 20))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(error.getStatus().value()).isEqualTo(400);
                });
        verifyNoInteractions(context, trips);
    }

    @Test
    void missingCellIsExplicitAndExcludesIncompleteSeatFromWholeTripAvailability() {
        OperatorOccupancyQueryRepository queries = mock(OperatorOccupancyQueryRepository.class);
        OperatorTripOperationsService service = operationsService(queries, 2, 2);
        when(queries.occupancy(7L, 99L)).thenReturn(List.of(
                row(1, 1, InventoryStatus.AVAILABLE), row(1, 2, null),
                row(2, 1, InventoryStatus.AVAILABLE), row(2, 2, InventoryStatus.AVAILABLE)));

        var result = service.occupancy(user, 99L);

        assertThat(result.complete()).isFalse();
        assertThat(result.expectedInventoryCellCount()).isEqualTo(4);
        assertThat(result.actualInventoryCellCount()).isEqualTo(3);
        assertThat(result.missingInventoryCellCount()).isEqualTo(1);
        assertThat(result.wholeTripAvailableSeatCount()).isEqualTo(1);
        assertThat(result.seats().get(0).segments().get(1).missing()).isTrue();
        assertThat(result.seats().get(0).segments().get(1).status()).isNull();
    }

    @Test
    void completeMatrixRetainsAllFourInventoryStatuses() {
        OperatorOccupancyQueryRepository queries = mock(OperatorOccupancyQueryRepository.class);
        OperatorTripOperationsService service = operationsService(queries, 2, 2);
        when(queries.occupancy(7L, 99L)).thenReturn(List.of(
                row(1, 1, InventoryStatus.AVAILABLE), row(1, 2, InventoryStatus.HELD),
                row(2, 1, InventoryStatus.BOOKED), row(2, 2, InventoryStatus.BLOCKED)));

        var result = service.occupancy(user, 99L);

        assertThat(result.complete()).isTrue();
        assertThat(result.missingInventoryCellCount()).isZero();
        assertThat(result.seats()).flatExtracting(seat -> seat.segments())
                .extracting(OperatorTripOperationsDtos.SeatSegmentState::status)
                .containsExactly(InventoryStatus.AVAILABLE, InventoryStatus.HELD,
                        InventoryStatus.BOOKED, InventoryStatus.BLOCKED);
    }

    private TripService tripService() {
        return new TripService(trips, stops, segments, seats, context, aggregateCreator);
    }

    private OperatorTripOperationsService operationsService(
            OperatorOccupancyQueryRepository queries, long seatCount, long segmentCount) {
        memberOf(7L);
        Trip trip = mock(Trip.class);
        when(trip.getStatus()).thenReturn(TripStatus.SCHEDULED);
        when(trips.findOwnedById(99L, 7L)).thenReturn(Optional.of(trip));
        when(seats.countByTripId(99L)).thenReturn(seatCount);
        when(segments.countByTripId(99L)).thenReturn(segmentCount);
        return new OperatorTripOperationsService(trips, context, queries, seats, segments, mock(com.busgo.operations.OperationsService.class), mock(com.busgo.trip.operations.LiveTripOperationsService.class));
    }

    private void memberOf(long operatorId) {
        TransportOperator operator = mock(TransportOperator.class);
        when(operator.getId()).thenReturn(operatorId);
        when(context.requireOperatorMember(user)).thenReturn(operator);
    }

    private static OperatorOccupancyQueryRepository.OccupancyRow row(
            long seatId, int segmentOrder, InventoryStatus status) {
        return new OperatorOccupancyQueryRepository.OccupancyRow(seatId, "A0" + seatId,
                1, (int) seatId, 1, SeatType.STANDARD, (long) segmentOrder, segmentOrder,
                (long) segmentOrder, (long) segmentOrder + 1, "From", "To", status,
                status == InventoryStatus.HELD ? LocalDateTime.of(2030, 9, 20, 2, 0) : null,
                null, null, null);
    }
}
