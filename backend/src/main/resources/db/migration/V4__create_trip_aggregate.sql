-- M4: transactional trip snapshots and per-segment seat inventory.
CREATE TABLE trips (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    operator_route_id BIGINT NOT NULL,
    bus_id BIGINT NOT NULL,
    departure_time DATETIME(6) NOT NULL,
    estimated_arrival_time DATETIME(6) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_trips_departure (departure_time),
    INDEX idx_trips_operator_route_departure (operator_route_id, departure_time),
    INDEX idx_trips_status_departure (status, departure_time),
    INDEX idx_trips_bus_schedule (bus_id, departure_time, estimated_arrival_time, status),
    CONSTRAINT fk_trips_operator_route FOREIGN KEY (operator_route_id) REFERENCES operator_routes (id),
    CONSTRAINT fk_trips_bus FOREIGN KEY (bus_id) REFERENCES buses (id),
    CONSTRAINT ck_trips_time CHECK (estimated_arrival_time > departure_time),
    CONSTRAINT ck_trips_status CHECK (status IN ('SCHEDULED', 'BOARDING', 'DEPARTED', 'COMPLETED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE trip_stops (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    source_route_stop_id BIGINT NULL,
    location_id BIGINT NOT NULL,
    stop_order INT NOT NULL,
    planned_arrival_time DATETIME(6) NULL,
    planned_departure_time DATETIME(6) NULL,
    allow_pickup BOOLEAN NOT NULL,
    allow_dropoff BOOLEAN NOT NULL,
    status VARCHAR(30) NOT NULL,
    CONSTRAINT uk_trip_stops_order UNIQUE (trip_id, stop_order),
    INDEX idx_trip_stops_location_trip (location_id, trip_id),
    INDEX idx_trip_stops_source (source_route_stop_id),
    CONSTRAINT fk_trip_stops_trip FOREIGN KEY (trip_id) REFERENCES trips (id),
    CONSTRAINT fk_trip_stops_source FOREIGN KEY (source_route_stop_id) REFERENCES route_stops (id),
    CONSTRAINT fk_trip_stops_location FOREIGN KEY (location_id) REFERENCES locations (id),
    CONSTRAINT ck_trip_stops_order CHECK (stop_order > 0),
    CONSTRAINT ck_trip_stops_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE trip_segments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    from_trip_stop_id BIGINT NOT NULL,
    to_trip_stop_id BIGINT NOT NULL,
    segment_order INT NOT NULL,
    CONSTRAINT uk_trip_segments_order UNIQUE (trip_id, segment_order),
    INDEX idx_trip_segments_from (from_trip_stop_id),
    INDEX idx_trip_segments_to (to_trip_stop_id),
    CONSTRAINT fk_trip_segments_trip FOREIGN KEY (trip_id) REFERENCES trips (id),
    CONSTRAINT fk_trip_segments_from FOREIGN KEY (from_trip_stop_id) REFERENCES trip_stops (id),
    CONSTRAINT fk_trip_segments_to FOREIGN KEY (to_trip_stop_id) REFERENCES trip_stops (id),
    CONSTRAINT ck_trip_segments_order CHECK (segment_order > 0),
    CONSTRAINT ck_trip_segments_distinct CHECK (from_trip_stop_id <> to_trip_stop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE trip_seats (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    source_seat_template_id BIGINT NULL,
    seat_code VARCHAR(20) NOT NULL,
    row_no INT NOT NULL,
    column_no INT NOT NULL,
    floor_no INT NOT NULL,
    seat_type VARCHAR(30) NOT NULL,
    CONSTRAINT uk_trip_seats_code UNIQUE (trip_id, seat_code),
    INDEX idx_trip_seats_source (source_seat_template_id),
    CONSTRAINT fk_trip_seats_trip FOREIGN KEY (trip_id) REFERENCES trips (id),
    CONSTRAINT fk_trip_seats_source FOREIGN KEY (source_seat_template_id) REFERENCES seat_templates (id),
    CONSTRAINT ck_trip_seats_row CHECK (row_no > 0),
    CONSTRAINT ck_trip_seats_column CHECK (column_no > 0),
    CONSTRAINT ck_trip_seats_floor CHECK (floor_no > 0),
    CONSTRAINT ck_trip_seats_type CHECK (seat_type IN ('STANDARD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE trip_seat_segment_inventory (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    trip_seat_id BIGINT NOT NULL,
    trip_segment_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    hold_token VARCHAR(100) NULL,
    held_by_user_id BIGINT NULL,
    hold_expires_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_inventory_seat_segment UNIQUE (trip_seat_id, trip_segment_id),
    INDEX idx_inventory_segment_status (trip_segment_id, status),
    INDEX idx_inventory_status_expiry (status, hold_expires_at),
    INDEX idx_inventory_held_by (held_by_user_id),
    CONSTRAINT fk_inventory_trip_seat FOREIGN KEY (trip_seat_id) REFERENCES trip_seats (id),
    CONSTRAINT fk_inventory_trip_segment FOREIGN KEY (trip_segment_id) REFERENCES trip_segments (id),
    CONSTRAINT fk_inventory_held_by FOREIGN KEY (held_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_inventory_status CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED', 'BLOCKED')),
    CONSTRAINT ck_inventory_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
