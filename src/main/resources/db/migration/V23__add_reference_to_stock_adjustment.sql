-- V23: Add reference column to stock_adjustment for linking adjustments to source documents
ALTER TABLE stock_adjustment
    ADD COLUMN reference VARCHAR(100) DEFAULT NULL AFTER notes;
