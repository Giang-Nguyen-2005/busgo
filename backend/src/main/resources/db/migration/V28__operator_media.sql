CREATE TABLE operator_media (
    id CHAR(36) PRIMARY KEY,
    operator_id BIGINT NOT NULL,
    bus_type_id BIGINT NULL,
    category ENUM('LOGO','INTERIOR','EXTERIOR','SEAT','OTHER') NOT NULL,
    alt VARCHAR(180) NOT NULL DEFAULT '',
    position INT NOT NULL DEFAULT 0,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    mime VARCHAR(16) NOT NULL,
    content MEDIUMBLOB NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    FOREIGN KEY (operator_id) REFERENCES transport_operators(id),
    FOREIGN KEY (bus_type_id) REFERENCES bus_types(id),
    INDEX media_gallery (operator_id,bus_type_id,position),
    CHECK ((category='LOGO') = (bus_type_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
