ALTER TABLE grn
    ADD COLUMN batch_id INT NULL AFTER no,
    ADD CONSTRAINT fk_grn_batch FOREIGN KEY (batch_id) REFERENCES item_batch(batch_id);
