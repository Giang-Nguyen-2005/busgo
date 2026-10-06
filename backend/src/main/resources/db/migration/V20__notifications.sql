CREATE TABLE notifications (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 recipient_user_id BIGINT NULL,
 operator_id BIGINT NULL,
 audience VARCHAR(20) NOT NULL,
 recipient_key VARCHAR(180) NOT NULL,
 event_key VARCHAR(160) NOT NULL,
 event_type VARCHAR(60) NOT NULL,
 title VARCHAR(200) NOT NULL,
 message TEXT NOT NULL,
 booking_id BIGINT NOT NULL,
 booking_code VARCHAR(50) NOT NULL,
 navigation_target VARCHAR(200) NULL,
 read_at DATETIME(6) NULL,
 created_at DATETIME(6) NOT NULL,
 UNIQUE KEY uk_notification_occurrence (recipient_key,event_key),
 INDEX idx_notification_history (recipient_user_id,audience,operator_id,created_at,id),
 INDEX idx_notification_unread (recipient_user_id,audience,operator_id,read_at),
 INDEX idx_notification_booking_event (booking_id,event_type),
 FOREIGN KEY (recipient_user_id) REFERENCES users(id),
 FOREIGN KEY (operator_id) REFERENCES transport_operators(id),
 FOREIGN KEY (booking_id) REFERENCES bookings(id),
 CHECK ((audience='CUSTOMER' AND recipient_user_id IS NOT NULL AND operator_id IS NULL)
 OR (audience='OPERATOR' AND recipient_user_id IS NOT NULL AND operator_id IS NOT NULL)
 OR (audience='ACCOUNTLESS' AND recipient_user_id IS NULL AND operator_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notification_deliveries (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 notification_id BIGINT NOT NULL,
 channel VARCHAR(20) NOT NULL DEFAULT 'EMAIL',
 destination VARCHAR(150) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 attempt_count INT NOT NULL DEFAULT 0,
 last_attempt_at DATETIME(6) NULL,
 next_attempt_at DATETIME(6) NOT NULL,
 sent_at DATETIME(6) NULL,
 error_summary VARCHAR(200) NULL,
 UNIQUE KEY uk_notification_channel (notification_id,channel),
 INDEX idx_delivery_due (status,next_attempt_at,id),
 FOREIGN KEY (notification_id) REFERENCES notifications(id),
 CHECK (channel='EMAIL'),
 CHECK (status IN ('PENDING','SENT','FAILED','SKIPPED')),
 CHECK (attempt_count BETWEEN 0 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notification_preferences (
 user_id BIGINT PRIMARY KEY,
 booking_payment_email BOOLEAN NOT NULL DEFAULT TRUE,
 booking_change_email BOOLEAN NOT NULL DEFAULT TRUE,
 trip_reminder_email BOOLEAN NOT NULL DEFAULT TRUE,
 FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
