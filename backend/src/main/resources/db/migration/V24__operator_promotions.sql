CREATE TABLE operator_vouchers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator_id BIGINT NOT NULL,
    funding_source VARCHAR(20) NOT NULL DEFAULT 'OPERATOR',
    code VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    discount_type VARCHAR(20) NOT NULL,
    discount_value DECIMAL(12,2) NOT NULL,
    max_discount DECIMAL(12,2),
    minimum_spend DECIMAL(12,2) NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    total_limit INT,
    customer_limit INT,
    state VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    operator_route_id BIGINT,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uq_voucher_operator_code(operator_id,code),
    UNIQUE KEY uq_voucher_operator_id(id,operator_id),
    FOREIGN KEY (operator_id) REFERENCES transport_operators(id),
    FOREIGN KEY (operator_route_id) REFERENCES operator_routes(id),
    FOREIGN KEY (created_by) REFERENCES users(id),
    FOREIGN KEY (updated_by) REFERENCES users(id),
    CONSTRAINT ck_voucher_terms CHECK (funding_source='OPERATOR' AND discount_type IN ('PERCENT','FIXED_AMOUNT')
        AND discount_value>0 AND (discount_type<>'PERCENT' OR discount_value<=100)
        AND (max_discount IS NULL OR max_discount>0) AND minimum_spend>=0
        AND starts_at<ends_at AND (total_limit IS NULL OR total_limit>0)
        AND (customer_limit IS NULL OR customer_limit>0) AND state IN ('DRAFT','ACTIVE','PAUSED'))
);
CREATE TABLE voucher_usages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    voucher_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    booking_id BIGINT NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    state VARCHAR(20) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    reserved_at DATETIME(6) NOT NULL,
    redeemed_at DATETIME(6),
    released_at DATETIME(6),
    FOREIGN KEY (voucher_id,operator_id) REFERENCES operator_vouchers(id,operator_id),
    FOREIGN KEY (booking_id) REFERENCES bookings(id),
    FOREIGN KEY (customer_id) REFERENCES users(id),
    INDEX ix_usage_capacity(voucher_id,state,expires_at,customer_id),
    CONSTRAINT ck_usage_state CHECK (
        (state='RESERVED' AND redeemed_at IS NULL AND released_at IS NULL) OR
        (state='REDEEMED' AND redeemed_at IS NOT NULL AND released_at IS NULL) OR
        (state='RELEASED' AND redeemed_at IS NULL AND released_at IS NOT NULL))
);
CREATE TABLE voucher_management_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    voucher_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    event VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    FOREIGN KEY(voucher_id) REFERENCES operator_vouchers(id),
    FOREIGN KEY(actor_id) REFERENCES users(id),
    INDEX ix_voucher_history(voucher_id,id)
);
ALTER TABLE bookings
    ADD gross_amount DECIMAL(12,2) NULL,
    ADD discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    ADD original_gross_amount DECIMAL(12,2) NULL,
    ADD original_discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    ADD voucher_id BIGINT NULL,
    ADD voucher_code VARCHAR(40) NULL,
    ADD voucher_name VARCHAR(120) NULL,
    ADD FOREIGN KEY(voucher_id) REFERENCES operator_vouchers(id);
UPDATE bookings SET gross_amount=total_amount, original_gross_amount=total_amount;
ALTER TABLE bookings MODIFY gross_amount DECIMAL(12,2) NOT NULL,
    MODIFY original_gross_amount DECIMAL(12,2) NOT NULL,
    ADD CONSTRAINT ck_booking_promotion CHECK(gross_amount-discount_amount=total_amount AND discount_amount>=0
        AND original_discount_amount>=0 AND original_discount_amount<=original_gross_amount);
ALTER TABLE booking_items
    ADD gross_amount DECIMAL(12,2) NULL,
    ADD discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    ADD original_gross_amount DECIMAL(12,2) NULL,
    ADD original_discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0;
UPDATE booking_items SET gross_amount=unit_price,original_gross_amount=unit_price;
-- Recover the oldest available recorded fare for previously modified items.
UPDATE booking_items i SET original_gross_amount=COALESCE((
    SELECT m.old_fare FROM booking_modification_items m
    JOIN booking_modifications h ON h.id=m.modification_id
    WHERE m.booking_item_id=i.id AND h.status='COMPLETED' ORDER BY h.id LIMIT 1
),i.unit_price);
UPDATE bookings b SET original_gross_amount=COALESCE((
    SELECT SUM(i.original_gross_amount) FROM booking_items i WHERE i.booking_id=b.id
),b.total_amount);
ALTER TABLE booking_items MODIFY gross_amount DECIMAL(12,2) NOT NULL,
    MODIFY original_gross_amount DECIMAL(12,2) NOT NULL,
    ADD CONSTRAINT ck_item_promotion CHECK(gross_amount-discount_amount=unit_price AND discount_amount>=0
        AND original_discount_amount>=0 AND original_discount_amount<=original_gross_amount);
ALTER TABLE booking_modification_items
    ADD old_gross DECIMAL(12,2) NULL,
    ADD new_gross DECIMAL(12,2) NULL,
    ADD old_discount DECIMAL(12,2) NOT NULL DEFAULT 0,
    ADD new_discount DECIMAL(12,2) NOT NULL DEFAULT 0;
UPDATE booking_modification_items SET old_gross=old_fare,new_gross=new_fare;
ALTER TABLE booking_modification_items MODIFY old_gross DECIMAL(12,2) NOT NULL,
    MODIFY new_gross DECIMAL(12,2) NOT NULL;
