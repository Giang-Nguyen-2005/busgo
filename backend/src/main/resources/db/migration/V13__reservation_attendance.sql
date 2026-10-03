-- Attendance belongs to a reserved passenger; tickets still require payment.
ALTER TABLE ticket_boarding ADD COLUMN booking_item_id BIGINT NULL;
UPDATE ticket_boarding a JOIN tickets t ON t.id=a.ticket_id
SET a.booking_item_id=t.booking_item_id;
ALTER TABLE ticket_boarding
 MODIFY booking_item_id BIGINT NOT NULL,
 MODIFY ticket_id BIGINT NULL,
 ADD UNIQUE KEY uk_attendance_booking_item(booking_item_id),
 ADD CONSTRAINT fk_attendance_booking_item FOREIGN KEY(booking_item_id) REFERENCES booking_items(id),
 ADD CONSTRAINT ck_unpaid_attendance CHECK(ticket_id IS NOT NULL OR status='NO_SHOW');
