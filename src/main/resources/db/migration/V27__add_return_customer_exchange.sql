-- V27: Add customer assignment and resolved invoice reference to return table
-- Supports Exchange refund method: assign return to a customer,
-- and record which new invoice consumed the exchange credit.

ALTER TABLE `return`
    ADD COLUMN `customer_id`         INT         NULL,
    ADD COLUMN `resolved_invoice_no` VARCHAR(20) NULL;

ALTER TABLE `return`
    ADD CONSTRAINT `fk_return_customer`
        FOREIGN KEY (`customer_id`) REFERENCES `customer` (`customer_id`)
        ON DELETE SET NULL;
