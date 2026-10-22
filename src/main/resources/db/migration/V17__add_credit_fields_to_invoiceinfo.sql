-- V17: Link invoices to customers and track credit resolve dates
ALTER TABLE invoiceinfo
    ADD COLUMN customer_id         INT  DEFAULT NULL AFTER employee_id,
    ADD COLUMN credit_resolve_date DATE DEFAULT NULL AFTER customer_id;

ALTER TABLE invoiceinfo
    ADD CONSTRAINT fk_inv_customer FOREIGN KEY (customer_id) REFERENCES customer (customer_id);
