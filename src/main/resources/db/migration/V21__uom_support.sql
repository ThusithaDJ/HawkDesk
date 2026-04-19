-- V21: Multi-Unit of Measure support
-- Adds sell_unit and conversion_factor to item table.
-- Convention: 1 stock_unit = conversion_factor sell_units
-- e.g. stock = "m", sell = "cm", factor = 100  →  500 cm ÷ 100 = 5 m deducted from stock

ALTER TABLE item
    ADD COLUMN sell_unit       VARCHAR(20) DEFAULT NULL,
    ADD COLUMN conversion_factor DOUBLE    DEFAULT 1.0;

-- Global UoM preset catalogue managed from Settings → Units of Measure
CREATE TABLE uom_preset (
    id   INT         NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

-- Seed with commonly used units
INSERT INTO uom_preset (name) VALUES
    ('pcs'), ('dozen'), ('box'),
    ('kg'), ('g'), ('mg'),
    ('m'), ('cm'), ('mm'),
    ('L'), ('mL');
