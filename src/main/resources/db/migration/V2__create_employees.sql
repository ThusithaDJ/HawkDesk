-- V2: Create employees table for authentication and role management

CREATE TABLE IF NOT EXISTS employees (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(128) NOT NULL,
    role            ENUM('OWNER','MANAGER','CASHIER','STOCK_KEEPER') NOT NULL DEFAULT 'CASHIER',
    pin_hash        VARCHAR(60)  NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login      DATETIME     DEFAULT NULL,
    failed_attempts INT          NOT NULL DEFAULT 0,
    locked_until    DATETIME     DEFAULT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Insert a default Owner account (PIN: 1234 — BCrypt hash, must be changed after first login)
-- BCrypt hash of '1234' with cost factor 10:
INSERT INTO employees (name, role, pin_hash, active)
VALUES ('Admin', 'OWNER', '$2a$10$QTO6QbO8LW/TJlVRQETu0OGa5yj4PrG3NVDghjIdNzkOvAOIzAetq', TRUE);
