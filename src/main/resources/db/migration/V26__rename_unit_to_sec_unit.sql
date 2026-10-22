-- Rename the item.unit column to sec_unit.
-- sell_unit is the primary selling unit shown to customers.
-- sec_unit (formerly unit) is the secondary/sub-unit.
ALTER TABLE item
    CHANGE unit sec_unit VARCHAR(20) DEFAULT 'pcs';
