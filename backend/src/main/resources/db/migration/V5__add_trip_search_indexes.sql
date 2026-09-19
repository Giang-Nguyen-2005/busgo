-- M5: indexes for snapshot-based customer journey search and exact fare lookup.
CREATE INDEX idx_trip_stops_pickup_search
    ON trip_stops (location_id, allow_pickup, planned_departure_time, trip_id, stop_order);

CREATE INDEX idx_fares_exact_journey
    ON operator_route_fares
        (operator_route_id, from_route_stop_id, to_route_stop_id, status);
