-- M9: mock payment confirmation, booking status history, and one e-ticket per booking item.
CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    method VARCHAR(30) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    transaction_reference VARCHAR(100) NOT NULL,
    paid_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    paid_booking_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status = 'PAID' THEN booking_id ELSE NULL END) STORED,
    INDEX idx_payments_booking (booking_id),
    CONSTRAINT uk_payments_transaction_reference UNIQUE (transaction_reference),
    CONSTRAINT uk_payments_one_paid_booking UNIQUE (paid_booking_id),
    CONSTRAINT fk_payments_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT ck_payments_method CHECK (method IN ('MOCK_QR')),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'REFUNDED')),
    CONSTRAINT ck_payments_paid_at CHECK (
        (status = 'PAID' AND paid_at IS NOT NULL) OR status <> 'PAID'
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE booking_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    from_status VARCHAR(30) NOT NULL,
    to_status VARCHAR(30) NOT NULL,
    changed_by_user_id BIGINT NULL,
    note VARCHAR(500) NULL,
    changed_at DATETIME(6) NOT NULL,
    INDEX idx_booking_status_history_booking_changed (booking_id, changed_at),
    INDEX idx_booking_status_history_changed_by (changed_by_user_id),
    CONSTRAINT fk_booking_status_history_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_booking_status_history_user FOREIGN KEY (changed_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_booking_status_history_from CHECK
        (from_status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT ck_booking_status_history_to CHECK
        (to_status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT ck_booking_status_history_changed CHECK (from_status <> to_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tickets (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ticket_code VARCHAR(50) NOT NULL,
    booking_id BIGINT NOT NULL,
    booking_item_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL,
    passenger_name VARCHAR(100) NOT NULL,
    seat_code VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_tickets_code UNIQUE (ticket_code),
    CONSTRAINT uk_tickets_booking_item UNIQUE (booking_item_id),
    INDEX idx_tickets_booking (booking_id),
    INDEX idx_tickets_payment (payment_id),
    CONSTRAINT fk_tickets_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_tickets_booking_item FOREIGN KEY (booking_item_id) REFERENCES booking_items (id),
    CONSTRAINT fk_tickets_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
