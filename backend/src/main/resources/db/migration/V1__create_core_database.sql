-- Milestone 1 only. Flyway owns DDL; Hibernate validates these tables.
CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE roles (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uk_roles_code UNIQUE (code),
    CONSTRAINT ck_roles_code CHECK (code IN ('CUSTOMER', 'OPERATOR_STAFF', 'OPERATOR_ADMIN', 'SYSTEM_ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    INDEX idx_user_roles_role (role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE transport_operators (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    code VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NULL,
    email VARCHAR(150) NULL,
    address VARCHAR(255) NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_operators_code UNIQUE (code),
    CONSTRAINT ck_operators_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE operator_staff (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    operator_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    staff_code VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_staff_operator_user UNIQUE (operator_id, user_id),
    INDEX idx_staff_user (user_id),
    CONSTRAINT fk_staff_operator FOREIGN KEY (operator_id) REFERENCES transport_operators (id),
    CONSTRAINT fk_staff_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_staff_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE locations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    province VARCHAR(100) NULL,
    district VARCHAR(100) NULL,
    address VARCHAR(255) NULL,
    latitude DECIMAL(10,7) NULL,
    longitude DECIMAL(10,7) NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT ck_locations_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE routes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    origin_location_id BIGINT NOT NULL,
    destination_location_id BIGINT NOT NULL,
    estimated_distance_km DECIMAL(8,2) NOT NULL,
    estimated_duration_min INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_routes_origin (origin_location_id),
    INDEX idx_routes_destination (destination_location_id),
    CONSTRAINT fk_routes_origin FOREIGN KEY (origin_location_id) REFERENCES locations (id),
    CONSTRAINT fk_routes_destination FOREIGN KEY (destination_location_id) REFERENCES locations (id),
    CONSTRAINT ck_routes_distance CHECK (estimated_distance_km >= 0),
    CONSTRAINT ck_routes_duration CHECK (estimated_duration_min >= 0),
    CONSTRAINT ck_routes_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE route_stops (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    route_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    stop_order INT NOT NULL,
    allow_pickup BOOLEAN NOT NULL,
    allow_dropoff BOOLEAN NOT NULL,
    estimated_offset_minutes INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    CONSTRAINT uk_route_stops_order UNIQUE (route_id, stop_order),
    CONSTRAINT uk_route_stops_location UNIQUE (route_id, location_id),
    INDEX idx_route_stops_location (location_id),
    CONSTRAINT fk_route_stops_route FOREIGN KEY (route_id) REFERENCES routes (id),
    CONSTRAINT fk_route_stops_location FOREIGN KEY (location_id) REFERENCES locations (id),
    CONSTRAINT ck_route_stops_order CHECK (stop_order > 0),
    CONSTRAINT ck_route_stops_offset CHECK (estimated_offset_minutes >= 0),
    CONSTRAINT ck_route_stops_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE operator_routes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    operator_id BIGINT NOT NULL,
    route_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_operator_routes_pair UNIQUE (operator_id, route_id),
    INDEX idx_operator_routes_route (route_id),
    CONSTRAINT fk_operator_routes_operator FOREIGN KEY (operator_id) REFERENCES transport_operators (id),
    CONSTRAINT fk_operator_routes_route FOREIGN KEY (route_id) REFERENCES routes (id),
    CONSTRAINT ck_operator_routes_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Cross-table membership/order checks are application validation in M3.
-- These FKs establish existence without changing the documented fare schema.
CREATE TABLE operator_route_fares (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    operator_route_id BIGINT NOT NULL,
    from_route_stop_id BIGINT NOT NULL,
    to_route_stop_id BIGINT NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_fares_operator_route (operator_route_id),
    INDEX idx_fares_from_stop (from_route_stop_id),
    INDEX idx_fares_to_stop (to_route_stop_id),
    CONSTRAINT fk_fares_operator_route FOREIGN KEY (operator_route_id) REFERENCES operator_routes (id),
    CONSTRAINT fk_fares_from_stop FOREIGN KEY (from_route_stop_id) REFERENCES route_stops (id),
    CONSTRAINT fk_fares_to_stop FOREIGN KEY (to_route_stop_id) REFERENCES route_stops (id),
    CONSTRAINT ck_fares_price CHECK (price >= 0),
    CONSTRAINT ck_fares_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bus_types (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    seat_count INT NOT NULL,
    description TEXT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT ck_bus_types_count CHECK (seat_count >= 0),
    CONSTRAINT ck_bus_types_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE seat_templates (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    bus_type_id BIGINT NOT NULL,
    seat_code VARCHAR(20) NOT NULL,
    row_no INT NOT NULL,
    column_no INT NOT NULL,
    floor_no INT NOT NULL,
    seat_type VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL,
    CONSTRAINT uk_seat_templates_code UNIQUE (bus_type_id, seat_code),
    CONSTRAINT fk_seat_templates_bus_type FOREIGN KEY (bus_type_id) REFERENCES bus_types (id),
    CONSTRAINT ck_seat_templates_type CHECK (seat_type IN ('STANDARD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE buses (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    operator_id BIGINT NOT NULL,
    bus_type_id BIGINT NOT NULL,
    license_plate VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    CONSTRAINT uk_buses_license_plate UNIQUE (license_plate),
    INDEX idx_buses_operator (operator_id),
    INDEX idx_buses_bus_type (bus_type_id),
    CONSTRAINT fk_buses_operator FOREIGN KEY (operator_id) REFERENCES transport_operators (id),
    CONSTRAINT fk_buses_bus_type FOREIGN KEY (bus_type_id) REFERENCES bus_types (id),
    CONSTRAINT ck_buses_status CHECK (status IN ('AVAILABLE', 'MAINTENANCE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
