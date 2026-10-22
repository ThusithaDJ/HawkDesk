ALTER TABLE stock_adjustment
    ADD COLUMN batch_id INT NULL AFTER item_id,
    ADD CONSTRAINT fk_stock_adj_batch FOREIGN KEY (batch_id) REFERENCES item_batch(batch_id);
