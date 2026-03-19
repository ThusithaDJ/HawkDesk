-- V5: Stock adjustments and write-offs log

CREATE TABLE IF NOT EXISTS stock_adjustment (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    item_id         BIGINT       NOT NULL,
    adjustment_type ENUM('ADD','REMOVE','SET','WRITEOFF') NOT NULL,
    qty_before      INT          NOT NULL,
    qty_change      INT          NOT NULL,
    qty_after       INT          NOT NULL,
    reason          VARCHAR(128) NOT NULL,
    notes           VARCHAR(512) DEFAULT NULL,
    loss_value      DECIMAL(12,2) DEFAULT NULL,
    employee_id     BIGINT       DEFAULT NULL,
    adjusted_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_adj_item FOREIGN KEY (item_id)     REFERENCES item      (item_id),
    CONSTRAINT fk_adj_emp  FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
