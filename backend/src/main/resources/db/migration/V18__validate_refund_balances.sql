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
       OR (NEW.modification_id IS NULL AND NEW.amount <> paid_amount-already_refunded) THEN
        SIGNAL SQLSTATE '23000' SET MESSAGE_TEXT='Invalid refund payment snapshot or remaining balance';
    END IF;
    IF NEW.modification_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM booking_modifications WHERE id=NEW.modification_id AND booking_id=payment_booking
            AND status IN ('HELD','AWAITING_PAYMENT')
    ) THEN
        SIGNAL SQLSTATE '23000' SET MESSAGE_TEXT='Refund modification does not own the payment';
    END IF;
END$$
DELIMITER ;
