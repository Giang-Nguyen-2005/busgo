-- M8: historical booking snapshots created from active customer-owned seat holds.
CREATE TABLE bookings (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_code VARCHAR(50) NOT NULL,
    customer_id BIGINT NOT NULL,
    trip_id BIGINT NOT NULL,
    pickup_trip_stop_id BIGINT NOT NULL,
    dropoff_trip_stop_id BIGINT NOT NULL,
    contact_name VARCHAR(100) NOT NULL,
    contact_phone VARCHAR(20) NOT NULL,
    contact_email VARCHAR(150) NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_bookings_code UNIQUE (booking_code),
    INDEX idx_bookings_customer_created (customer_id, created_at),
    INDEX idx_bookings_trip (trip_id),
    CONSTRAINT fk_bookings_customer FOREIGN KEY (customer_id) REFERENCES users (id),
    CONSTRAINT fk_bookings_trip FOREIGN KEY (trip_id) REFERENCES trips (id),
    CONSTRAINT fk_bookings_pickup FOREIGN KEY (pickup_trip_stop_id) REFERENCES trip_stops (id),
    CONSTRAINT fk_bookings_dropoff FOREIGN KEY (dropoff_trip_stop_id) REFERENCES trip_stops (id),
    CONSTRAINT ck_bookings_amount CHECK (total_amount > 0),
    CONSTRAINT ck_bookings_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT ck_bookings_distinct_stops CHECK (pickup_trip_stop_id <> dropoff_trip_stop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE booking_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    trip_seat_id BIGINT NOT NULL,
    seat_code VARCHAR(20) NOT NULL,
    passenger_name VARCHAR(100) NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_booking_items_booking_seat UNIQUE (booking_id, trip_seat_id),
    INDEX idx_booking_items_booking (booking_id),
    INDEX idx_booking_items_trip_seat (trip_seat_id),
    CONSTRAINT fk_booking_items_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_booking_items_trip_seat FOREIGN KEY (trip_seat_id) REFERENCES trip_seats (id),
    CONSTRAINT ck_booking_items_price CHECK (unit_price > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE trip_seat_segment_inventory
    ADD COLUMN booking_item_id BIGINT NULL AFTER hold_expires_at,
    ADD INDEX idx_inventory_booking_item (booking_item_id),
    ADD CONSTRAINT fk_inventory_booking_item
        FOREIGN KEY (booking_item_id) REFERENCES booking_items (id);
