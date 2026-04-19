-- V25: Cash account management — cash drawer and bank accounts with income/expense ledger

CREATE TABLE cash_account (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(100) NOT NULL,
    account_type ENUM('CASH_DRAWER','SAVINGS','CURRENT') NOT NULL,
    balance      DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    notes        VARCHAR(500) DEFAULT NULL,
    is_active    TINYINT(1)   NOT NULL DEFAULT 1,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE income (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    account_id       BIGINT       NOT NULL,
    amount           DECIMAL(12,2) NOT NULL,
    description      VARCHAR(500) DEFAULT NULL,
    reference        VARCHAR(100) DEFAULT NULL,
    transaction_date DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    employee_id      BIGINT       DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_income_account (account_id),
    INDEX idx_income_date    (transaction_date),
    CONSTRAINT fk_income_account  FOREIGN KEY (account_id)  REFERENCES cash_account(id),
    CONSTRAINT fk_income_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE expense (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    account_id       BIGINT       NOT NULL,
    amount           DECIMAL(12,2) NOT NULL,
    description      VARCHAR(500) DEFAULT NULL,
    reference        VARCHAR(100) DEFAULT NULL,
    transaction_date DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    employee_id      BIGINT       DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_expense_account (account_id),
    INDEX idx_expense_date    (transaction_date),
    CONSTRAINT fk_expense_account  FOREIGN KEY (account_id)  REFERENCES cash_account(id),
    CONSTRAINT fk_expense_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed the three default accounts
INSERT INTO cash_account (name, account_type) VALUES
    ('Cash Drawer',     'CASH_DRAWER'),
    ('Savings Account', 'SAVINGS'),
    ('Current Account', 'CURRENT');
