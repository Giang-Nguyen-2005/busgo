CREATE TABLE operator_employees (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, operator_id BIGINT NOT NULL,
 employee_code VARCHAR(50) NOT NULL, full_name VARCHAR(100) NOT NULL, phone VARCHAR(20) NOT NULL,
 status VARCHAR(20) NOT NULL, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_employee_code(operator_id,employee_code),
 FOREIGN KEY(operator_id) REFERENCES transport_operators(id),
 CHECK(status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB;
CREATE TABLE employee_capabilities (
 employee_id BIGINT NOT NULL, capability VARCHAR(20) NOT NULL,
 PRIMARY KEY(employee_id,capability), FOREIGN KEY(employee_id) REFERENCES operator_employees(id),
 CHECK(capability IN ('DRIVER','ATTENDANT'))
) ENGINE=InnoDB;
CREATE TABLE driver_profiles (
 employee_id BIGINT PRIMARY KEY, licence_number VARCHAR(50) NOT NULL,
 licence_class VARCHAR(30) NOT NULL, licence_expiry_date DATE NOT NULL,
 FOREIGN KEY(employee_id) REFERENCES operator_employees(id)
) ENGINE=InnoDB;
CREATE TABLE trip_crew_assignments (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, trip_id BIGINT NOT NULL, employee_id BIGINT NOT NULL,
 duty VARCHAR(20) NOT NULL, assigned_at DATETIME(6) NOT NULL, assigned_by BIGINT NOT NULL,
 released_at DATETIME(6), released_by BIGINT, version BIGINT NOT NULL DEFAULT 0,
 active_employee_id BIGINT GENERATED ALWAYS AS (CASE WHEN released_at IS NULL THEN employee_id ELSE NULL END) STORED,
 UNIQUE KEY uk_active_crew(trip_id,active_employee_id,duty),
 INDEX idx_employee_overlap(employee_id,released_at,trip_id),
 FOREIGN KEY(trip_id) REFERENCES trips(id), FOREIGN KEY(employee_id) REFERENCES operator_employees(id),
 FOREIGN KEY(assigned_by) REFERENCES users(id), FOREIGN KEY(released_by) REFERENCES users(id),
 CHECK(duty IN ('DRIVER','ATTENDANT'))
) ENGINE=InnoDB;
CREATE TABLE ticket_boarding (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, ticket_id BIGINT NOT NULL UNIQUE,
 status VARCHAR(20) NOT NULL, checked_in_at DATETIME(6), checked_in_by BIGINT,
 boarded_at DATETIME(6), boarded_by BIGINT, no_show_at DATETIME(6), no_show_by BIGINT,
 actual_boarding_stop_id BIGINT, pickup_stop_id BIGINT NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(ticket_id) REFERENCES tickets(id), FOREIGN KEY(pickup_stop_id) REFERENCES trip_stops(id),
 FOREIGN KEY(actual_boarding_stop_id) REFERENCES trip_stops(id),
 FOREIGN KEY(checked_in_by) REFERENCES users(id), FOREIGN KEY(boarded_by) REFERENCES users(id),
 FOREIGN KEY(no_show_by) REFERENCES users(id),
 CHECK(status IN ('EXPECTED','CHECKED_IN','BOARDED','NO_SHOW'))
) ENGINE=InnoDB;
CREATE TABLE trip_stop_operations (
 trip_id BIGINT NOT NULL, stop_id BIGINT NOT NULL, pickup_closed_at DATETIME(6) NOT NULL,
 pickup_closed_by BIGINT NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(trip_id,stop_id), FOREIGN KEY(trip_id) REFERENCES trips(id),
 FOREIGN KEY(stop_id) REFERENCES trip_stops(id), FOREIGN KEY(pickup_closed_by) REFERENCES users(id)
) ENGINE=InnoDB;
CREATE TABLE operational_history (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, trip_id BIGINT NOT NULL,
 entity_type VARCHAR(30) NOT NULL, entity_id BIGINT NOT NULL, action VARCHAR(40) NOT NULL,
 actor_id BIGINT NOT NULL, occurred_at DATETIME(6) NOT NULL, reason VARCHAR(500),
 INDEX idx_operations_history(trip_id,id), FOREIGN KEY(trip_id) REFERENCES trips(id),
 FOREIGN KEY(actor_id) REFERENCES users(id)
) ENGINE=InnoDB;
