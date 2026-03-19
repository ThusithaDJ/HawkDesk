-- V4: Audit log for every meaningful data change

CREATE TABLE IF NOT EXISTS audit_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    table_name   VARCHAR(64)  NOT NULL,
    record_id    BIGINT       DEFAULT NULL,
    action       ENUM('INSERT','UPDATE','DELETE','LOGIN','LOGOUT','PERMISSION_DENIED') NOT NULL,
    old_value    JSON         DEFAULT NULL,
    new_value    JSON         DEFAULT NULL,
    employee_id  BIGINT       DEFAULT NULL,
    ip_address   VARCHAR(45)  DEFAULT NULL,
    performed_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes        VARCHAR(512) DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_emp FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
