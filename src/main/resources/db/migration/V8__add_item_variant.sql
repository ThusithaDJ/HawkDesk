-- V8: Add item_variant table — named price/cost variants linked to a parent item.
-- stock.variant_id (nullable FK) points to the variant when a named variant was received.

CREATE TABLE IF NOT EXISTS item_variant (
    variant_id  INT         NOT NULL AUTO_INCREMENT,
    item_id     INT         NOT NULL,
    sku         VARCHAR(45) NOT NULL,
    stat        VARCHAR(45) DEFAULT 'Active',
    PRIMARY KEY (variant_id),
    UNIQUE KEY uq_item_variant_sku (sku),
    CONSTRAINT fk_item_variant_item FOREIGN KEY (item_id) REFERENCES item(item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE stock
    ADD COLUMN variant_id INT NULL AFTER sku,
    ADD CONSTRAINT fk_stock_variant FOREIGN KEY (variant_id) REFERENCES item_variant(variant_id);
