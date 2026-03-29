-- Formal batch tracking table.
-- batch_number is auto-generated as B{YYYY}-{NNN} but can be overridden.
CREATE TABLE IF NOT EXISTS item_batch (
    batch_id      INT            NOT NULL AUTO_INCREMENT,
    item_id       INT            NOT NULL,
    grn_no        INT            NULL,
    batch_number  VARCHAR(20)    NOT NULL,
    batch_label   VARCHAR(100)   NULL,
    qty_received  INT            NOT NULL DEFAULT 0,
    qty_remaining INT            NOT NULL DEFAULT 0,
    cost_price    DECIMAL(12, 2) NOT NULL DEFAULT 0,
    expiry_date   DATE           NULL,
    status        VARCHAR(10)    NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    BIGINT         NULL,
    PRIMARY KEY (batch_id),
    UNIQUE KEY uq_item_batch_number (batch_number),
    CONSTRAINT fk_item_batch_item FOREIGN KEY (item_id)    REFERENCES item(item_id),
    CONSTRAINT fk_item_batch_grn  FOREIGN KEY (grn_no)     REFERENCES grninfo(grn_no),
    CONSTRAINT fk_item_batch_emp  FOREIGN KEY (created_by) REFERENCES employees(id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Link each stock record to its formal batch
ALTER TABLE stock
    ADD COLUMN batch_id INT NULL AFTER variant_id,
    ADD CONSTRAINT fk_stock_batch FOREIGN KEY (batch_id) REFERENCES item_batch(batch_id);
