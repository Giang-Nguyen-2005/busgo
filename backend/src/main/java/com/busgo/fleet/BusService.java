package com.busgo.fleet;

import com.busgo.common.exception.BusinessException;
import com.busgo.common.exception.ResourceNotFoundException;
import com.busgo.common.response.PagedResponse;
import com.busgo.common.security.CurrentUser;
import com.busgo.fleet.FleetDtos.*;
import com.busgo.fleet.entity.*;
import com.busgo.fleet.repository.BusRepository;
import com.busgo.operator.OperatorContextService;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusService {
    private final BusRepository buses;
    private final BusTypeService busTypes;
    private final OperatorContextService context;
    private final MaintenanceService maintenance;
    private final FleetMaintenanceGuard guard;
    private final java.time.Clock clock;

    public BusService(BusRepository buses, BusTypeService busTypes, OperatorContextService context,
            MaintenanceService maintenance, FleetMaintenanceGuard guard, java.time.Clock clock) {
        this.buses = buses;
        this.busTypes = busTypes;
        this.context = context;
        this.maintenance = maintenance;
        this.guard = guard;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PagedResponse<BusResponse> list(CurrentUser user, BusStatus status, Long busTypeId,
            String query, int page, int size) {
        Long operatorId = context.requireAdminOperator(user).getId();
        var result = buses.search(operatorId, status, busTypeId, query == null ? "" : query.strip(),
                PageRequest.of(page, size, Sort.by("licensePlate").ascending().and(Sort.by("id"))));
        return PagedResponse.from(result.map(this::response));
    }

    @Transactional
    public BusResponse create(CurrentUser user, CreateBusRequest request) {
        var operator = context.requireAdminOperator(user);
        String plate = normalizePlate(request.licensePlate());
        if (buses.existsByLicensePlateIgnoreCase(plate)) throw duplicatePlate();
        Bus bus = new Bus();
        bus.setOperator(operator);
        bus.setBusType(busTypes.requireUsable(request.busTypeId()));
        bus.setLicensePlate(plate);
        bus.setStatus(BusStatus.AVAILABLE);
        return response(save(bus));
    }

    @Transactional(readOnly = true)
    public BusResponse get(CurrentUser user, Long id) {
        Long operatorId = context.requireAdminOperator(user).getId();
        return response(owned(id, operatorId));
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public BusResponse update(CurrentUser user, Long id, UpdateBusRequest request) {
        Long operatorId = context.requireAdminOperator(user).getId();
        Bus bus = guard.lockFleetBus(operatorId, id);
        if (request.licensePlate() != null) {
            String plate = normalizePlate(request.licensePlate());
            if (buses.existsByLicensePlateIgnoreCaseAndIdNot(plate, id)) throw duplicatePlate();
            bus.setLicensePlate(plate);
        }
        if (request.busTypeId() != null) bus.setBusType(busTypes.requireUsable(request.busTypeId()));
        if (request.status() != null) {
            if (request.status() == BusStatus.AVAILABLE && guard.hasActive(id))
                throw FleetMaintenanceGuard.conflict("BUS_MAINTENANCE_CONFLICT", "Complete active maintenance before restoring availability.", null);
            if (request.status() != bus.getStatus() && guard.hasActive(id)
                    && request.status() != BusStatus.INACTIVE)
                throw FleetMaintenanceGuard.conflict("BUS_MAINTENANCE_CONFLICT", "Only INACTIVE is allowed while maintenance is active.", null);
            guard.changeStatus(bus, request.status(), "OPERATOR_STATUS_CHANGED", null, user.id(),
                    com.busgo.common.time.BusGoTime.utc(clock.instant()));
        }
        return response(save(bus));
    }

    private Bus owned(Long id, Long operatorId) {
        return buses.findOwnedById(id, operatorId)
                .orElseThrow(() -> new ResourceNotFoundException("BUS_NOT_FOUND", "Bus was not found."));
    }

    private Bus save(Bus bus) {
        try {
            return buses.saveAndFlush(bus);
        } catch (DataIntegrityViolationException ex) {
            throw duplicatePlate();
        }
    }

    private BusResponse response(Bus bus) {
        BusType type = bus.getBusType();
        return new BusResponse(bus.getId(), bus.getLicensePlate(), bus.getStatus(),
                new BusTypeSummary(type.getId(), type.getName(), type.getSeatCount()),
                maintenance.readiness(bus.getOperator().getId(), bus));
    }

    private static String normalizePlate(String plate) {
        return plate.strip().toUpperCase(Locale.ROOT);
    }

    private static BusinessException duplicatePlate() {
        return new BusinessException("LICENSE_PLATE_ALREADY_EXISTS", "License plate is already registered.",
                HttpStatus.CONFLICT, null);
    }
}
