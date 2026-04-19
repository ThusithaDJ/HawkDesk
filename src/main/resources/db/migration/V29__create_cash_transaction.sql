-- V29: Unified transaction ledger — all financial events in one table.
-- Tracks Sales, GRN payments, Return Refunds, and manual Income/Expense entries.

CREATE TABLE cash_transaction (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    type             ENUM('SALE','GRN','RETURN_REFUND','INCOME','EXPENSE') NOT NULL,
    reference        VARCHAR(100)  DEFAULT NULL,
    description      VARCHAR(500)  DEFAULT NULL,
    amount           DECIMAL(12,2) NOT NULL,
    transaction_date DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    account_id       BIGINT        DEFAULT NULL,
    employee_id      BIGINT        DEFAULT NULL,
    category         VARCHAR(100)  DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_ctx_type    (type),
    INDEX idx_ctx_date    (transaction_date),
    INDEX idx_ctx_account (account_id),
    CONSTRAINT fk_ctx_account  FOREIGN KEY (account_id)  REFERENCES cash_account(id) ON DELETE SET NULL,
    CONSTRAINT fk_ctx_employee FOREIGN KEY (employee_id) REFERENCES employees(id)    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
