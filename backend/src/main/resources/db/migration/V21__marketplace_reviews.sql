ALTER TABLE transport_operators
    ADD COLUMN public_description VARCHAR(2000) NULL,
    ADD COLUMN logo_url VARCHAR(500) NULL;

-- Ownership/operator/trip derive from the booking; no independently mutable foreign context.
CREATE TABLE marketplace_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    rating INT NOT NULL,
    review_text VARCHAR(2000) NOT NULL,
    response_text VARCHAR(2000) NULL,
    response_updated_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    active_booking_id BIGINT GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN booking_id ELSE NULL END) STORED,
    CONSTRAINT uk_review_active_booking UNIQUE (active_booking_id),
    CONSTRAINT fk_review_booking FOREIGN KEY (booking_id) REFERENCES bookings(id),
    CONSTRAINT ck_review_rating CHECK (rating BETWEEN 1 AND 5),
    INDEX idx_review_booking_visible (booking_id, deleted_at, rating),
    INDEX idx_review_recent (deleted_at, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
