-- V7: Add variant SKU column to stock table
-- Allows multiple price/cost variants of the same item (different batches/suppliers)
ALTER TABLE stock ADD COLUMN sku VARCHAR(45) NULL AFTER batch;
