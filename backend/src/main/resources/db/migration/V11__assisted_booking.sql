-- Historical commerce remains WEB with account ownership.
ALTER TABLE bookings
    MODIFY customer_id BIGINT NULL,
    MODIFY contact_email VARCHAR(150) NULL,
    ADD source VARCHAR(30) NOT NULL DEFAULT 'WEB',
    ADD payment_method VARCHAR(30) NOT NULL DEFAULT 'MOCK_ONLINE',
    ADD payment_token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD CONSTRAINT uk_booking_payment_token UNIQUE (payment_token_hash),
    ADD CONSTRAINT ck_booking_source CHECK (source IN ('WEB', 'PHONE')),
    ADD CONSTRAINT ck_booking_source_owner CHECK (
        (source = 'WEB' AND customer_id IS NOT NULL AND payment_method = 'MOCK_ONLINE') OR
        (source = 'PHONE' AND customer_id IS NULL AND payment_method IN ('PAY_ON_BOARD', 'QR_TRANSFER')));

ALTER TABLE payments DROP CHECK ck_payments_method;
UPDATE payments SET method = 'MOCK_ONLINE' WHERE method = 'MOCK_QR';
ALTER TABLE payments
    ADD collected_by_user_id BIGINT NULL,
    ADD reference_note VARCHAR(500) NULL,
    ADD CONSTRAINT fk_payment_collector FOREIGN KEY (collected_by_user_id) REFERENCES users(id),
    ADD CONSTRAINT ck_payments_method CHECK (method IN ('MOCK_ONLINE', 'PAY_ON_BOARD', 'QR_TRANSFER'));
