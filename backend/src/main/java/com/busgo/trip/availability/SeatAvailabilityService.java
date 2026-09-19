package com.busgo.trip.availability;

import static com.busgo.common.time.BusGoTime.api;

import com.busgo.trip.availability.SeatAvailabilityDtos.*;
import com.busgo.trip.search.CustomerJourneyResolver;
import com.busgo.trip.search.SeatAvailabilityQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeatAvailabilityService {
    private final CustomerJourneyResolver journeys;
    private final SeatAvailabilityQueryRepository availability;

    public SeatAvailabilityService(CustomerJourneyResolver journeys,
            SeatAvailabilityQueryRepository availability) {
        this.journeys = journeys;
        this.availability = availability;
    }

    @Transactional(readOnly = true)
    public SeatMap get(Long tripId, Long pickupLocationId, Long dropoffLocationId) {
        var journey = journeys.resolve(tripId, pickupLocationId, dropoffLocationId);
        var pickup = journey.pickup();
        var dropoff = journey.dropoff();
        var seats = availability.seatMap(tripId, pickup.getStopOrder(), dropoff.getStopOrder(),
                        journey.requiredSegmentCount()).stream()
                .map(row -> new Seat(row.tripSeatId(), row.seatCode(), row.row(), row.column(),
                        row.floor(), row.seatType(), row.available()))
                .toList();
        long availableSeatCount = seats.stream().filter(Seat::available).count();
        return new SeatMap(tripId,
                new Pickup(pickup.getId(), pickup.getLocation().getId(),
                        pickup.getLocation().getName(), api(pickup.getPlannedDepartureTime())),
                new Dropoff(dropoff.getId(), dropoff.getLocation().getId(),
                        dropoff.getLocation().getName(), api(dropoff.getPlannedArrivalTime())),
                journey.fare().getPrice(), availableSeatCount, seats);
    }
}
