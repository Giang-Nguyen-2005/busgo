ALTER TABLE booking_items
 ADD cancelled BOOLEAN NOT NULL DEFAULT FALSE,
 ADD INDEX idx_booking_active_items (booking_id,cancelled,id),
 ADD active_trip_seat_id BIGINT GENERATED ALWAYS AS (CASE WHEN cancelled=FALSE THEN trip_seat_id ELSE NULL END) STORED,
 DROP INDEX uk_booking_items_booking_seat,
 ADD UNIQUE KEY uk_booking_active_seat (booking_id,active_trip_seat_id);

CREATE TABLE partial_cancellations (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 code VARCHAR(50) NOT NULL UNIQUE,
 booking_id BIGINT NOT NULL,
 operator_id BIGINT NOT NULL,
 actor_type VARCHAR(30) NOT NULL,
 actor_user_id BIGINT NOT NULL,
 actor_name VARCHAR(150) NOT NULL,
 current_total DECIMAL(12,2) NOT NULL,
 cancelled_amount DECIMAL(12,2) NOT NULL,
 new_total DECIMAL(12,2) NOT NULL,
 net_collected DECIMAL(12,2) NOT NULL,
 refund_required DECIMAL(12,2) NOT NULL,
 new_amount_due DECIMAL(12,2) NOT NULL,
 trip_id BIGINT NOT NULL,
 journey_name VARCHAR(500) NOT NULL,
 departure_time DATETIME(6) NOT NULL,
 completed_at DATETIME(6) NOT NULL,
 INDEX idx_partial_history (booking_id,id),
 FOREIGN KEY (booking_id) REFERENCES bookings(id),
 FOREIGN KEY (operator_id) REFERENCES transport_operators(id),
 FOREIGN KEY (actor_user_id) REFERENCES users(id),
 FOREIGN KEY (trip_id) REFERENCES trips(id),
 CHECK (actor_type IN ('CUSTOMER','OPERATOR_ADMIN')),
 CHECK (current_total>0 AND cancelled_amount>0 AND new_total>0 AND current_total=cancelled_amount+new_total),
 CHECK (net_collected>=0 AND refund_required=GREATEST(net_collected-new_total,0) AND new_amount_due=GREATEST(new_total-net_collected,0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE partial_cancellation_items (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 cancellation_id BIGINT NOT NULL,
 booking_item_id BIGINT NOT NULL UNIQUE,
 passenger_name VARCHAR(100) NOT NULL,
 old_seat_id BIGINT NOT NULL,
 seat_code VARCHAR(20) NOT NULL,
 amount DECIMAL(12,2) NOT NULL,
 ticket_id BIGINT NULL,
 FOREIGN KEY (cancellation_id) REFERENCES partial_cancellations(id),
 FOREIGN KEY (booking_item_id) REFERENCES booking_items(id),
 FOREIGN KEY (old_seat_id) REFERENCES trip_seats(id),
 FOREIGN KEY (ticket_id) REFERENCES tickets(id),
 CHECK (amount>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V17 used an unnamed reason check; resolve its generated name without assuming numbering.
SET @m21_reason_check=(SELECT CONSTRAINT_NAME FROM information_schema.CHECK_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA=DATABASE() AND CHECK_CLAUSE LIKE '%MODIFICATION_FARE_DIFFERENCE%' LIMIT 1);
SET @m21_drop=CONCAT('ALTER TABLE refunds DROP CHECK ',@m21_reason_check,' ');
PREPARE m21_stmt FROM @m21_drop;
EXECUTE m21_stmt;
DEALLOCATE PREPARE m21_stmt;
ALTER TABLE refunds
 ADD partial_cancellation_id BIGINT NULL,
 ADD FOREIGN KEY (partial_cancellation_id) REFERENCES partial_cancellations(id),
 DROP INDEX uk_refund_cancellation,
 DROP COLUMN cancellation_payment_id;
ALTER TABLE refunds
 ADD cancellation_payment_id BIGINT GENERATED ALWAYS AS (CASE WHEN modification_id IS NULL AND partial_cancellation_id IS NULL THEN payment_id ELSE NULL END) STORED,
 ADD UNIQUE KEY uk_refund_cancellation (cancellation_payment_id),
 ADD UNIQUE KEY uk_refund_partial (payment_id,partial_cancellation_id),
 ADD CONSTRAINT ck_refund_context CHECK (
 (reason_code IN ('CUSTOMER_CANCELLED','OPERATOR_CANCELLED') AND modification_id IS NULL AND partial_cancellation_id IS NULL)
 OR (reason_code='MODIFICATION_FARE_DIFFERENCE' AND modification_id IS NOT NULL AND partial_cancellation_id IS NULL)
 OR (reason_code='PARTIAL_CANCELLATION' AND modification_id IS NULL AND partial_cancellation_id IS NOT NULL));

DROP TRIGGER validate_refund_balance;
-- Keep V1.5's rejection of incorrect cancellation refunds, while permitting M20 partial refunds.
-- Commerce services lock payment rows before inserting; the trigger validates each snapshot.
DELIMITER $$
CREATE TRIGGER validate_refund_balance BEFORE INSERT ON refunds FOR EACH ROW
BEGIN
    DECLARE paid_amount DECIMAL(12,2);
    DECLARE paid_time DATETIME(6);
    DECLARE paid_status VARCHAR(30);
    DECLARE payment_booking BIGINT;
    DECLARE already_refunded DECIMAL(12,2);
    SELECT amount,paid_at,status,booking_id INTO paid_amount,paid_time,paid_status,payment_booking
        FROM payments WHERE id=NEW.payment_id FOR SHARE;
    SELECT COALESCE(SUM(amount),0) INTO already_refunded FROM refunds WHERE payment_id=NEW.payment_id FOR SHARE;
    IF paid_status <> 'PAID' OR paid_time IS NULL OR NOT (paid_time <=> NEW.payment_paid_at)
       OR NEW.amount > paid_amount-already_refunded
       OR (NEW.modification_id IS NULL AND NEW.partial_cancellation_id IS NULL AND NEW.amount <> paid_amount-already_refunded) THEN
        SIGNAL SQLSTATE '23000' SET MESSAGE_TEXT='Invalid refund payment snapshot or remaining balance';
    END IF;
    IF NEW.modification_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM booking_modifications WHERE id=NEW.modification_id AND booking_id=payment_booking
            AND status IN ('HELD','AWAITING_PAYMENT')
    ) THEN
        SIGNAL SQLSTATE '23000' SET MESSAGE_TEXT='Refund modification does not own the payment';
    END IF;
    IF NEW.partial_cancellation_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM partial_cancellations WHERE id=NEW.partial_cancellation_id AND booking_id=payment_booking
    ) THEN
        SIGNAL SQLSTATE '23000' SET MESSAGE_TEXT='Partial cancellation does not own the payment';
    END IF;
END$$
DELIMITER ;

