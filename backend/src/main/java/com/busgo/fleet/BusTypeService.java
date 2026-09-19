package com.busgo.fleet;

import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.exception.BusinessException;
import com.busgo.common.exception.ResourceNotFoundException;
import com.busgo.fleet.FleetDtos.BusTypeResponse;
import com.busgo.fleet.FleetDtos.SeatTemplateResponse;
import com.busgo.fleet.entity.BusType;
import com.busgo.fleet.entity.SeatTemplate;
import com.busgo.fleet.repository.BusTypeRepository;
import com.busgo.fleet.repository.SeatTemplateRepository;
import java.util.HashSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusTypeService {
    private final BusTypeRepository busTypes;
    private final SeatTemplateRepository seats;

    public BusTypeService(BusTypeRepository busTypes, SeatTemplateRepository seats) {
        this.busTypes = busTypes;
        this.seats = seats;
    }

    @Transactional(readOnly = true)
    public List<BusTypeResponse> listActive() {
        return busTypes.findByStatusOrderByNameAsc(ActiveStatus.ACTIVE).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public BusTypeResponse getActive(Long id) {
        return response(requireUsable(id));
    }

    public BusType requireUsable(Long id) {
        BusType type = busTypes.findByIdAndStatus(id, ActiveStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("BUS_TYPE_NOT_FOUND", "Bus type was not found."));
        List<SeatTemplate> layout = seats.findByBusTypeIdAndActiveTrueOrderByFloorAscRowAscColumnAsc(id);
        if (type.getSeatCount() == null || type.getSeatCount() <= 0 || layout.size() != type.getSeatCount()) {
            throw invalid();
        }
        var positions = new HashSet<String>();
        var codes = new HashSet<String>();
        for (SeatTemplate seat : layout) {
            if (seat.getSeatCode() == null || seat.getSeatCode().isBlank()
                    || seat.getRow() == null || seat.getRow() <= 0
                    || seat.getColumn() == null || seat.getColumn() <= 0
                    || seat.getFloor() == null || seat.getFloor() <= 0
                    || !codes.add(seat.getSeatCode().strip().toUpperCase(java.util.Locale.ROOT))
                    || !positions.add(seat.getFloor() + ":" + seat.getRow() + ":" + seat.getColumn())) {
                throw invalid();
            }
        }
        return type;
    }

    private BusTypeResponse response(BusType type) {
        List<SeatTemplateResponse> layout = seats
                .findByBusTypeIdAndActiveTrueOrderByFloorAscRowAscColumnAsc(type.getId()).stream()
                .map(seat -> new SeatTemplateResponse(seat.getId(), seat.getSeatCode(), seat.getRow(),
                        seat.getColumn(), seat.getFloor(), seat.getSeatType(), seat.isActive()))
                .toList();
        return new BusTypeResponse(type.getId(), type.getName(), type.getSeatCount(), type.getDescription(),
                type.getStatus(), layout);
    }

    private static BusinessException invalid() {
        return new BusinessException("INVALID_BUS_TYPE", "Bus type has an invalid active seat layout.",
                HttpStatus.CONFLICT, null);
    }
}
