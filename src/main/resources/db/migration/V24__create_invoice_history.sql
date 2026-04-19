-- V24: Invoice history table — tracks lifecycle events for each invoice
CREATE TABLE invoice_history (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    invoice_no   VARCHAR(50)  NOT NULL,
    event_type   VARCHAR(30)  NOT NULL COMMENT 'CREATED, PAID, VOIDED, RETURNED, PARTIAL_RETURN',
    event_date   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    amount       DECIMAL(12,2) DEFAULT NULL COMMENT 'Amount involved in this event (payment, return, etc.)',
    notes        VARCHAR(500) DEFAULT NULL,
    employee_id  BIGINT       DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_invoice_history_invoice_no (invoice_no),
    INDEX idx_invoice_history_event_date (event_date),
    CONSTRAINT fk_invoice_history_employee
        FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
