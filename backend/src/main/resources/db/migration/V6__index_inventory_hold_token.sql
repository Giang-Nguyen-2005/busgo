-- M7: customer hold lookup and release are keyed by the opaque hold token.
CREATE INDEX idx_inventory_hold_token
    ON trip_seat_segment_inventory (hold_token);
