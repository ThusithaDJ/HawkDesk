-- V16: Customer table for credit sales
CREATE TABLE customer (
    customer_id    INT PRIMARY KEY AUTO_INCREMENT,
    name           VARCHAR(128) NOT NULL,
    phone          VARCHAR(20)  DEFAULT NULL,
    address        VARCHAR(256) DEFAULT NULL,
    max_debt_amount DECIMAL(12,2) DEFAULT 0.00,
    created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP
);
