-- Query-time reporting: existing trip/route, item, payment booking, ticket booking,
-- attendance-item and inventory seat/segment indexes already cover ownership/joins.
CREATE INDEX idx_bookings_created_trip ON bookings(created_at, trip_id);
CREATE INDEX idx_bookings_cancelled_trip ON bookings(cancelled_at, trip_id);
CREATE INDEX idx_payments_paid_booking ON payments(paid_at, booking_id);
CREATE INDEX idx_refunds_refunded_payment ON refunds(refunded_at, payment_id);
CREATE INDEX idx_tickets_created_booking ON tickets(created_at, booking_id);
