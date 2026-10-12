ALTER TABLE transport_operators DROP CHECK ck_operators_status;
ALTER TABLE transport_operators
 ADD CONSTRAINT ck_operators_status CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED')),
 ADD legal_name VARCHAR(150) NULL,
 ADD legal_code VARCHAR(50) NULL,
 ADD legal_contact VARCHAR(100) NULL,
 ADD website VARCHAR(500) NULL,
 ADD onboarded_at DATETIME(6) NULL,
 ADD UNIQUE KEY uk_operator_legal_code (legal_code),
 ADD INDEX idx_operator_status (status);

-- Existing installed operators with an administrator are already provisioned.
UPDATE transport_operators o SET onboarded_at=o.created_at WHERE EXISTS
 (SELECT 1 FROM operator_staff s JOIN user_roles ur ON ur.user_id=s.user_id
 JOIN roles r ON r.id=ur.role_id WHERE s.operator_id=o.id AND r.code='OPERATOR_ADMIN');

CREATE TABLE operator_applications (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(150) NOT NULL, legal_name VARCHAR(150) NOT NULL,
 legal_code VARCHAR(50) NOT NULL,
 email VARCHAR(150) NOT NULL, phone VARCHAR(20) NOT NULL,
 address VARCHAR(255) NOT NULL, contact_name VARCHAR(100) NOT NULL,
 admin_email VARCHAR(150) NOT NULL, website VARCHAR(500) NULL,
 public_description VARCHAR(2000) NULL,
 status VARCHAR(30) NOT NULL,
 reserved_legal_code VARCHAR(50) GENERATED ALWAYS AS
   (CASE WHEN status IN ('SUBMITTED','UNDER_REVIEW','APPROVED') THEN legal_code ELSE NULL END) STORED,
 operator_id BIGINT NULL,
 submitted_at DATETIME(6) NOT NULL,
 UNIQUE KEY uk_application_legal_reservation (reserved_legal_code),
 UNIQUE KEY uk_application_operator (operator_id),
 INDEX idx_application_status_date (status,submitted_at,id),
 FOREIGN KEY (operator_id) REFERENCES transport_operators(id),
 CHECK (status IN ('SUBMITTED','UNDER_REVIEW','APPROVED','REJECTED')),
 CHECK ((status='APPROVED' AND operator_id IS NOT NULL) OR (status<>'APPROVED' AND operator_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE operator_admin_invitations (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 application_id BIGINT NOT NULL, operator_id BIGINT NOT NULL,
 intended_email VARCHAR(150) NOT NULL,
 token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at DATETIME(6) NOT NULL, expires_at DATETIME(6) NOT NULL,
 accepted_at DATETIME(6) NULL, revoked_at DATETIME(6) NULL,
 current_application_id BIGINT GENERATED ALWAYS AS
  (CASE WHEN accepted_at IS NULL AND revoked_at IS NULL THEN application_id ELSE NULL END) STORED,
 UNIQUE KEY uk_invitation_hash (token_hash),
 UNIQUE KEY uk_invitation_current (current_application_id),
 INDEX idx_invitation_application (application_id,id),
 FOREIGN KEY (application_id) REFERENCES operator_applications(id),
 FOREIGN KEY (operator_id) REFERENCES transport_operators(id),
 CHECK (expires_at>created_at),
 CHECK (accepted_at IS NULL OR revoked_at IS NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE operator_onboarding_history (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 application_id BIGINT NULL, operator_id BIGINT NULL,
 event_type VARCHAR(50) NOT NULL, actor_category VARCHAR(30) NOT NULL,
 actor_id BIGINT NULL, old_state VARCHAR(30) NULL, new_state VARCHAR(30) NOT NULL,
 note VARCHAR(1000) NULL, created_at DATETIME(6) NOT NULL,
 INDEX idx_onboarding_application (application_id,id),
 INDEX idx_onboarding_operator (operator_id,id),
 FOREIGN KEY (application_id) REFERENCES operator_applications(id),
 FOREIGN KEY (operator_id) REFERENCES transport_operators(id),
 FOREIGN KEY (actor_id) REFERENCES users(id),
 CHECK (actor_category IN ('APPLICANT','SYSTEM_ADMIN','INVITEE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Accountless onboarding messages contain no secret and need no fake booking.
ALTER TABLE notifications MODIFY booking_id BIGINT NULL, MODIFY booking_code VARCHAR(50) NULL;
