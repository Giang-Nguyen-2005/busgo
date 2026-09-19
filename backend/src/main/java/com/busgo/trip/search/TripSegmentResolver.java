package com.busgo.trip.search;

import com.busgo.common.exception.BusinessException;
import com.busgo.trip.entity.TripSegment;
import com.busgo.trip.entity.TripStop;
import com.busgo.trip.repository.TripSegmentRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TripSegmentResolver {
    private final TripSegmentRepository segments;

    public TripSegmentResolver(TripSegmentRepository segments) {
        this.segments = segments;
    }

    public List<TripSegment> resolve(Long tripId, TripStop pickup, TripStop dropoff) {
        int requiredCount = dropoff.getStopOrder() - pickup.getStopOrder();
        List<TripSegment> required = segments.findJourneySegments(
                tripId, pickup.getStopOrder(), dropoff.getStopOrder());
        if (required.size() != requiredCount || required.isEmpty()
                || !required.get(0).getFromTripStop().getId().equals(pickup.getId())
                || !required.get(required.size() - 1).getToTripStop().getId().equals(dropoff.getId())) {
            throw notBookable();
        }
        for (int index = 0; index < required.size(); index++) {
            TripSegment segment = required.get(index);
            if (segment.getSegmentOrder() != pickup.getStopOrder() + index
                    || index > 0 && !required.get(index - 1).getToTripStop().getId()
                            .equals(segment.getFromTripStop().getId())) {
                throw notBookable();
            }
        }
        return required;
    }

    private static BusinessException notBookable() {
        return new BusinessException("TRIP_NOT_BOOKABLE",
                "The selected journey does not have a complete consecutive segment snapshot.",
                HttpStatus.CONFLICT, null);
    }
}
