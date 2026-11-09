package com.busgo.location;

import com.busgo.location.LocationDtos.LocationResponse;
import com.busgo.location.repository.LocationRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationService {
    private final LocationRepository locations;

    public LocationService(LocationRepository locations) {
        this.locations = locations;
    }

    /** Bounded exact lookup; autocomplete's first page is never a registry. */
    @Transactional(readOnly = true)
    public List<LocationResponse> lookup(List<Long> ids) {
        return locations.findByIdInAndStatus(ids, com.busgo.common.entity.ActiveStatus.ACTIVE).stream()
                .sorted(java.util.Comparator.comparing(com.busgo.location.entity.Location::getId))
                .map(location -> new LocationResponse(location.getId(), location.getName(),
                        location.getProvince(), location.getDistrict())).toList();
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> search(String query) {
        String normalized = query == null ? "" : query.strip();
        return locations.searchActive(normalized, PageRequest.of(0, 20)).stream()
                .map(location -> new LocationResponse(location.getId(), location.getName(),
                        location.getProvince(), location.getDistrict()))
                .toList();
    }
}
