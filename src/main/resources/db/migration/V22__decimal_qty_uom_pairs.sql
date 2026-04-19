-- V22: Decimal stock quantities + UoM pair registration
ALTER TABLE stock   MODIFY COLUMN qty DECIMAL(15,4) DEFAULT 0;
ALTER TABLE invoice MODIFY COLUMN qty DECIMAL(15,4) DEFAULT 0;
ALTER TABLE `return` MODIFY COLUMN qty DECIMAL(15,4) DEFAULT 0;

ALTER TABLE uom_preset
    ADD COLUMN secondary_unit    VARCHAR(20)   DEFAULT NULL,
    ADD COLUMN conversion_factor DECIMAL(15,4) DEFAULT 1.0000;
