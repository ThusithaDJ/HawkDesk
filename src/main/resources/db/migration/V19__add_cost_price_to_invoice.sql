-- V19: Store unit cost at time of sale on each invoice line item.
-- This allows accurate profit calculations even after stock cost prices are updated.
ALTER TABLE invoice
    ADD COLUMN cost_price DOUBLE DEFAULT NULL AFTER sub_total;
