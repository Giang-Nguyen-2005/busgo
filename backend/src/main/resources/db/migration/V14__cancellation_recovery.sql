-- Additive: legacy reservations deliberately retain a NULL payment deadline.
ALTER TABLE bookings
    ADD payment_due_at DATETIME(6) NULL,
    ADD cancelled_at DATETIME(6) NULL,
    ADD cancelled_by_user_id BIGINT NULL,
    ADD cancellation_reason VARCHAR(30) NULL,
    ADD cancellation_note VARCHAR(500) NULL,
    ADD CONSTRAINT fk_booking_canceller FOREIGN KEY (cancelled_by_user_id) REFERENCES users(id),
    ADD CONSTRAINT ck_booking_cancel_reason CHECK (cancellation_reason IS NULL OR cancellation_reason IN ('CUSTOMER_CANCELLED','OPERATOR_CANCELLED','PAYMENT_TIMEOUT')),
    ADD CONSTRAINT ck_booking_deadline CHECK (payment_method <> 'PAY_ON_BOARD' OR payment_due_at IS NULL),
    ADD INDEX idx_booking_expiry (status, payment_due_at, id);

ALTER TABLE booking_status_history ADD reason_code VARCHAR(30) NULL;

ALTER TABLE tickets
    ADD status VARCHAR(10) NOT NULL DEFAULT 'VALID',
    ADD voided_at DATETIME(6) NULL,
    ADD voided_by_user_id BIGINT NULL,
    ADD void_reason VARCHAR(30) NULL,
    ADD CONSTRAINT fk_ticket_void_actor FOREIGN KEY (voided_by_user_id) REFERENCES users(id),
    ADD CONSTRAINT ck_ticket_validity CHECK ((status='VALID' AND voided_at IS NULL AND void_reason IS NULL) OR (status='VOID' AND voided_at IS NOT NULL AND void_reason IS NOT NULL));

-- Full amount and original successful timestamp are enforced by a composite foreign key.
ALTER TABLE payments
    ADD CONSTRAINT uk_payment_refund_snapshot UNIQUE (id,amount,paid_at),
    ADD CONSTRAINT ck_payment_success_timestamp CHECK ((status IN ('PAID','REFUNDED') AND paid_at IS NOT NULL) OR (status IN ('PENDING','FAILED') AND paid_at IS NULL));

CREATE TABLE refunds (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    payment_paid_at DATETIME(6) NOT NULL,
    refunded_at DATETIME(6) NOT NULL,
    refunded_by BIGINT NULL,
    reason_code VARCHAR(30) NOT NULL,
    note VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_refund_payment UNIQUE (payment_id),
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id,amount,payment_paid_at) REFERENCES payments(id,amount,paid_at),
    CONSTRAINT fk_refund_actor FOREIGN KEY (refunded_by) REFERENCES users(id),
    CONSTRAINT ck_refund_amount CHECK (amount > 0),
    CONSTRAINT ck_refund_reason CHECK (reason_code IN ('CUSTOMER_CANCELLED','OPERATOR_CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

