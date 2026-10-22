-- Rename total -> net_total and add sub_total, gross_total, tax to invoiceinfo
ALTER TABLE invoiceinfo
  CHANGE COLUMN `total` `net_total` DECIMAL(15,2),
  ADD COLUMN `sub_total`   DECIMAL(15,2) AFTER `net_total`,
  ADD COLUMN `gross_total` DECIMAL(15,2) AFTER `sub_total`,
  ADD COLUMN `tax`         DECIMAL(15,2) DEFAULT 0 AFTER `gross_total`;

-- Backfill existing rows: sub_total = net_total + discount, gross_total = net_total + discount, tax = 0
UPDATE invoiceinfo
SET sub_total   = COALESCE(net_total, 0) + COALESCE(discount, 0),
    gross_total = COALESCE(net_total, 0) + COALESCE(discount, 0),
    tax         = 0
WHERE sub_total IS NULL;
