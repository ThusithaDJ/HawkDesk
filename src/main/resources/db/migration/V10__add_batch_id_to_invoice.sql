ALTER TABLE invoice
    ADD COLUMN batch_id INT NULL AFTER batch,
    ADD CONSTRAINT fk_invoice_batch FOREIGN KEY (batch_id) REFERENCES item_batch(batch_id);
